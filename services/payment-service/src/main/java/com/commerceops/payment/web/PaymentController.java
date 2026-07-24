package com.commerceops.payment.web;

import com.commerceops.common.web.RawResponse;
import com.commerceops.payment.config.ChaosSettings;
import com.commerceops.payment.repository.PaymentRepository;
import com.commerceops.payment.service.PaymentService;
import com.commerceops.payment.web.dto.AuthorizeRazorpayRequest;
import com.commerceops.payment.web.dto.ChaosResponse;
import com.commerceops.payment.web.dto.CreateRazorpayOrderRequest;
import com.commerceops.payment.web.dto.CreateRazorpayOrderResponse;
import com.commerceops.payment.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final ChaosSettings chaosSettings;
    private final PaymentService paymentService;

    public PaymentController(
            PaymentRepository paymentRepository,
            ChaosSettings chaosSettings,
            PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.chaosSettings = chaosSettings;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> list() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @GetMapping("/by-order/{orderId}")
    public List<PaymentResponse> byOrder(@PathVariable("orderId") String orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @PostMapping("/chaos")
    public ChaosResponse setChaos(@RequestParam("failureRate") double failureRate) {
        chaosSettings.setFailureRate(failureRate);
        return new ChaosResponse(chaosSettings.getFailureRate());
    }

    @PostMapping("/razorpay/orders")
    public CreateRazorpayOrderResponse createRazorpayOrder(@Valid @RequestBody CreateRazorpayOrderRequest request) {
        return paymentService.createRazorpayOrder(request);
    }

    @PostMapping("/razorpay/authorize")
    public PaymentResponse authorize(@Valid @RequestBody AuthorizeRazorpayRequest request) {
        return PaymentResponse.from(paymentService.authorize(request));
    }

    @RawResponse
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Map<String, String>> razorpayWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(body, signature == null ? "" : signature);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
