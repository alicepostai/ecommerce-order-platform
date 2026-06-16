package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.dto.InitiatePaymentCommand;
import com.ecommerce.orders.application.dto.PaymentResult;
import com.ecommerce.orders.application.port.in.GetPaymentUseCase;
import com.ecommerce.orders.application.port.in.InitiatePaymentUseCase;
import com.ecommerce.orders.infrastructure.web.dto.InitiatePaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final InitiatePaymentUseCase initiatePayment;
    private final GetPaymentUseCase getPayment;

    public PaymentController(InitiatePaymentUseCase initiatePayment, GetPaymentUseCase getPayment) {
        this.initiatePayment = initiatePayment;
        this.getPayment = getPayment;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payments:write')")
    public ResponseEntity<PaymentResult> initiatePayment(@Valid @RequestBody InitiatePaymentRequest req) {
        var command = new InitiatePaymentCommand(req.orderId(), req.method().cardToken());
        var result = initiatePayment.initiatePayment(command);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.id())).body(result);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_payments:read')")
    public PaymentResult getPayment(@PathVariable UUID paymentId) {
        return getPayment.getById(paymentId);
    }
}
