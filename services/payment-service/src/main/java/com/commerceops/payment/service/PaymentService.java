package com.commerceops.payment.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.payment.config.ChaosSettings;
import com.commerceops.payment.config.PaymentProperties;
import com.commerceops.payment.domain.Payment;
import com.commerceops.payment.domain.PaymentStatus;
import com.commerceops.payment.razorpay.RazorpayClientFacade;
import com.commerceops.payment.repository.PaymentRepository;
import com.commerceops.payment.web.dto.AuthorizeRazorpayRequest;
import com.commerceops.payment.web.dto.CreateRazorpayOrderRequest;
import com.commerceops.payment.web.dto.CreateRazorpayOrderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final BigDecimal NINETY_NINE_CENTS = new BigDecimal("0.99");

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final ChaosSettings chaosSettings;
    private final PaymentProperties paymentProperties;
    private final RazorpayClientFacade razorpay;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            OutboxService outboxService,
            ChaosSettings chaosSettings,
            PaymentProperties paymentProperties,
            RazorpayClientFacade razorpay,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
        this.chaosSettings = chaosSettings;
        this.paymentProperties = paymentProperties;
        this.razorpay = razorpay;
        this.objectMapper = objectMapper;
    }

    public CreateRazorpayOrderResponse createRazorpayOrder(CreateRazorpayOrderRequest request) {
        String currency = request.currency() == null ? "INR" : request.currency().toUpperCase();
        if (!"INR".equals(currency) && paymentProperties.isRazorpay()) {
            throw new IllegalArgumentException("Razorpay checkout only supports INR");
        }
        long paise = RazorpayClientFacade.toPaise(request.amount());
        String receipt = request.receipt() != null ? request.receipt() : "rcpt-" + UUID.randomUUID();
        RazorpayClientFacade.CreatedOrder created = razorpay.createOrder(paise, currency, receipt);
        return new CreateRazorpayOrderResponse(
                razorpay.keyId(),
                created.razorpayOrderId(),
                created.amountPaise(),
                created.currency(),
                paymentProperties.getProvider());
    }

    @Transactional
    public Payment authorize(AuthorizeRazorpayRequest request) {
        Optional<Payment> byProviderPayment = paymentRepository.findByProviderPaymentId(request.razorpayPaymentId());
        if (byProviderPayment.isPresent()) {
            Payment existing = byProviderPayment.get();
            if (!existing.getOrderId().equals(request.orderId())) {
                throw new IllegalArgumentException("Payment already linked to another order");
            }
            return existing;
        }

        Optional<Payment> byProviderOrder = paymentRepository.findByProviderOrderId(request.razorpayOrderId());
        if (byProviderOrder.isPresent()
                && byProviderOrder.get().getStatus() == PaymentStatus.AUTHORIZED
                && byProviderOrder.get().getOrderId().equals(request.orderId())) {
            return byProviderOrder.get();
        }

        if (!razorpay.verifyPaymentSignature(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            throw new IllegalArgumentException("Invalid Razorpay payment signature");
        }

        Payment payment = byProviderOrder.orElseGet(Payment::new);
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount() != null ? request.amount() : BigDecimal.ZERO);
        payment.setCurrency(request.currency() != null ? request.currency().toUpperCase() : "INR");
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setProvider(paymentProperties.getProvider());
        payment.setProviderOrderId(request.razorpayOrderId());
        payment.setProviderPaymentId(request.razorpayPaymentId());
        payment.setFailureReason(null);
        payment = paymentRepository.save(payment);
        log.info("Payment AUTHORIZED for orderId={} providerPaymentId={}",
                payment.getOrderId(), payment.getProviderPaymentId());
        return payment;
    }

    @Transactional
    public Payment capture(Payloads.CapturePayment cmd, String correlationId, String causationId) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(cmd.paymentIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Capture already processed for idempotencyKey={}, skipping", cmd.paymentIdempotencyKey());
            return existing.get();
        }

        Optional<Payment> authorized = paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(cmd.orderId(), PaymentStatus.AUTHORIZED);

        if (authorized.isPresent()) {
            return captureAuthorized(authorized.get(), cmd, correlationId, causationId);
        }

        // Admin / simulated path: no prior authorize — create and capture in one step
        return captureSimulatedNew(cmd, correlationId, causationId);
    }

    private Payment captureAuthorized(
            Payment payment, Payloads.CapturePayment cmd, String correlationId, String causationId) {
        payment.setSagaId(cmd.sagaId());
        payment.setIdempotencyKey(cmd.paymentIdempotencyKey());
        if (cmd.amount() != null) {
            payment.setAmount(cmd.amount());
        }
        if (cmd.currency() != null) {
            payment.setCurrency(cmd.currency());
        }

        if (paymentProperties.isRazorpay() || "razorpay".equalsIgnoreCase(payment.getProvider())) {
            try {
                long paise = RazorpayClientFacade.toPaise(payment.getAmount());
                razorpay.capture(payment.getProviderPaymentId(), paise, payment.getCurrency());
            } catch (RuntimeException ex) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(truncate(ex.getMessage()));
                payment = paymentRepository.save(payment);
                publishPaymentFailed(payment, correlationId, causationId, payment.getFailureReason());
                log.warn("Razorpay capture failed for orderId={} reason={}",
                        payment.getOrderId(), payment.getFailureReason());
                return payment;
            }
        } else {
            String failureReason = simulateCaptureFailure(cmd);
            if (failureReason != null) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(failureReason);
                payment = paymentRepository.save(payment);
                publishPaymentFailed(payment, correlationId, causationId, failureReason);
                log.warn("Payment capture failed for orderId={} reason={}", payment.getOrderId(), failureReason);
                return payment;
            }
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment = paymentRepository.save(payment);
        publishPaymentCaptured(payment, correlationId, causationId);
        log.info("Payment captured for orderId={} paymentId={}", payment.getOrderId(), payment.getId());
        return payment;
    }

    private Payment captureSimulatedNew(Payloads.CapturePayment cmd, String correlationId, String causationId) {
        Payment payment = new Payment();
        payment.setOrderId(cmd.orderId());
        payment.setSagaId(cmd.sagaId());
        payment.setAmount(cmd.amount());
        payment.setCurrency(cmd.currency());
        payment.setIdempotencyKey(cmd.paymentIdempotencyKey());
        payment.setProvider(paymentProperties.getProvider());

        if (paymentProperties.isRazorpay()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("No AUTHORIZED payment found for order; authorize on checkout first");
            payment = paymentRepository.save(payment);
            publishPaymentFailed(payment, correlationId, causationId, payment.getFailureReason());
            return payment;
        }

        String failureReason = simulateCaptureFailure(cmd);
        if (failureReason != null) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            payment = paymentRepository.save(payment);
            publishPaymentFailed(payment, correlationId, causationId, failureReason);
            log.warn("Payment capture failed for orderId={} reason={}", payment.getOrderId(), failureReason);
        } else {
            payment.setStatus(PaymentStatus.CAPTURED);
            payment = paymentRepository.save(payment);
            publishPaymentCaptured(payment, correlationId, causationId);
            log.info("Payment captured for orderId={} paymentId={}", payment.getOrderId(), payment.getId());
        }
        return payment;
    }

    @Transactional
    public Payment refund(Payloads.RefundPayment cmd, String correlationId, String causationId) {
        Payment payment = resolvePayment(cmd.paymentId(), cmd.orderId());
        if (payment == null) {
            log.warn("Cannot refund: no payment found for paymentId={} orderId={}", cmd.paymentId(), cmd.orderId());
            return null;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment {} already refunded, skipping", payment.getId());
            return payment;
        }

        if (payment.getProviderPaymentId() != null
                && (paymentProperties.isRazorpay() || "razorpay".equalsIgnoreCase(payment.getProvider()))) {
            try {
                long paise = RazorpayClientFacade.toPaise(payment.getAmount());
                razorpay.refund(payment.getProviderPaymentId(), paise);
            } catch (RuntimeException ex) {
                log.error("Razorpay refund failed for paymentId={}: {}", payment.getId(), ex.getMessage());
                throw ex;
            }
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);
        publishPaymentRefunded(payment, correlationId, causationId);
        log.info("Payment refunded for orderId={} paymentId={}", payment.getOrderId(), payment.getId());
        return payment;
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!razorpay.verifyWebhookSignature(rawBody, signature)) {
            throw new IllegalArgumentException("Invalid Razorpay webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();
            JsonNode entity = root.path("payload").path("payment").path("entity");
            if (entity.isMissingNode()) {
                entity = root.path("payload").path("refund").path("entity");
            }
            String providerPaymentId = entity.path("id").asText(null);
            String orderId = entity.path("notes").path("commerce_order_id").asText(null);
            if (providerPaymentId == null) {
                return;
            }

            Optional<Payment> paymentOpt = paymentRepository.findByProviderPaymentId(providerPaymentId);
            if (paymentOpt.isEmpty() && orderId != null) {
                paymentOpt = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                        orderId, PaymentStatus.AUTHORIZED);
            }
            if (paymentOpt.isEmpty()) {
                log.info("Webhook {} for unknown payment {}", event, providerPaymentId);
                return;
            }
            Payment payment = paymentOpt.get();
            String correlationId = payment.getOrderId();

            switch (event) {
                case "payment.captured" -> {
                    if (payment.getStatus() == PaymentStatus.CAPTURED) {
                        return;
                    }
                    payment.setStatus(PaymentStatus.CAPTURED);
                    paymentRepository.save(payment);
                    publishPaymentCaptured(payment, correlationId, "webhook");
                }
                case "payment.failed" -> {
                    if (payment.getStatus() == PaymentStatus.FAILED) {
                        return;
                    }
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason("Razorpay webhook: payment.failed");
                    paymentRepository.save(payment);
                    publishPaymentFailed(payment, correlationId, "webhook", payment.getFailureReason());
                }
                case "refund.processed" -> {
                    if (payment.getStatus() == PaymentStatus.REFUNDED) {
                        return;
                    }
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    publishPaymentRefunded(payment, correlationId, "webhook");
                }
                default -> log.debug("Ignoring Razorpay webhook event {}", event);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process Razorpay webhook", ex);
        }
    }

    private Payment resolvePayment(String paymentId, String orderId) {
        if (paymentId != null) {
            try {
                Optional<Payment> byId = paymentRepository.findById(Long.valueOf(paymentId));
                if (byId.isPresent()) {
                    return byId.get();
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            Optional<Payment> byProvider = paymentRepository.findByProviderPaymentId(paymentId);
            if (byProvider.isPresent()) {
                return byProvider.get();
            }
        }
        return paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.CAPTURED)
                .orElse(null);
    }

    private String simulateCaptureFailure(Payloads.CapturePayment cmd) {
        if (cmd.orderId() != null && cmd.orderId().startsWith("FAIL-")) {
            return "Simulated failure: orderId marked FAIL-";
        }
        if ("FAIL".equalsIgnoreCase(cmd.currency())) {
            return "Simulated failure: currency marked FAIL";
        }
        if (endsInNinetyNineCents(cmd.amount())) {
            return "Simulated failure: amount ends in .99";
        }
        double rate = chaosSettings.getFailureRate();
        if (rate > 0.0 && ThreadLocalRandom.current().nextDouble() < rate) {
            return "Simulated failure: chaos failure-rate triggered (" + rate + ")";
        }
        return null;
    }

    private boolean endsInNinetyNineCents(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        BigDecimal fraction = amount.remainder(BigDecimal.ONE).abs().setScale(2, RoundingMode.HALF_UP);
        return fraction.compareTo(NINETY_NINE_CENTS) == 0;
    }

    private void publishPaymentCaptured(Payment payment, String correlationId, String causationId) {
        Payloads.PaymentCaptured payload = new Payloads.PaymentCaptured(
                payment.getOrderId(), payment.getSagaId(), String.valueOf(payment.getId()), payment.getAmount());
        enqueue(EventTypes.PAYMENT_CAPTURED, payment, correlationId, causationId, payload);
    }

    private void publishPaymentFailed(Payment payment, String correlationId, String causationId, String reason) {
        Payloads.PaymentFailed payload = new Payloads.PaymentFailed(payment.getOrderId(), payment.getSagaId(), reason);
        enqueue(EventTypes.PAYMENT_FAILED, payment, correlationId, causationId, payload);
    }

    private void publishPaymentRefunded(Payment payment, String correlationId, String causationId) {
        Payloads.PaymentRefunded payload = new Payloads.PaymentRefunded(
                payment.getOrderId(), payment.getSagaId(), String.valueOf(payment.getId()));
        enqueue(EventTypes.PAYMENT_REFUNDED, payment, correlationId, causationId, payload);
    }

    private void enqueue(String eventType, Payment payment, String correlationId, String causationId, Object payload) {
        EventEnvelope envelope = EventEnvelope.of(
                eventType,
                payment.getOrderId(),
                correlationId,
                causationId,
                payment.getSagaId(),
                UUID.randomUUID().toString(),
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.PAYMENT_EVENTS, envelope);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Razorpay error";
        }
        return message.length() > 250 ? message.substring(0, 250) : message;
    }
}
