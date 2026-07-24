package com.commerceops.payment.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);
    private static final String CONSUMER_GROUP = "payment-service";

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;

    public PaymentCommandListener(IdempotencyService idempotencyService, PaymentService paymentService) {
        this.idempotencyService = idempotencyService;
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = CONSUMER_GROUP)
    public void onMessage(String message) {
        EventEnvelope envelope = EventJson.read(message, EventEnvelope.class);

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.info("Duplicate command eventId={} eventType={} skipped", envelope.eventId(), envelope.eventType());
            return;
        }

        switch (envelope.eventType()) {
            case EventTypes.CAPTURE_PAYMENT -> {
                Payloads.CapturePayment cmd = EventJson.fromNode(envelope.payload(), Payloads.CapturePayment.class);
                paymentService.capture(cmd, envelope.correlationId(), envelope.eventId());
            }
            case EventTypes.REFUND_PAYMENT -> {
                Payloads.RefundPayment cmd = EventJson.fromNode(envelope.payload(), Payloads.RefundPayment.class);
                paymentService.refund(cmd, envelope.correlationId(), envelope.eventId());
            }
            default -> log.warn("Unhandled event type={} on topic={}", envelope.eventType(), Topics.PAYMENT_COMMANDS);
        }
    }
}
