package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_URI = "https://api.ecommerce.dev/problems/";

    private static final Map<String, HttpStatus> STATE_STATUSES = Map.of(
            "order-not-modifiable",      HttpStatus.CONFLICT,
            "order-not-confirmable",     HttpStatus.CONFLICT,
            "order-not-cancellable",     HttpStatus.CONFLICT,
            "order-not-confirmed",       HttpStatus.CONFLICT,
            "payment-already-pending",   HttpStatus.CONFLICT,
            "payment-attempts-exceeded", HttpStatus.CONFLICT,
            "invalid-payment-state",     HttpStatus.CONFLICT,
            "empty-order",               HttpStatus.UNPROCESSABLE_ENTITY
    );

    @ExceptionHandler(CustomerBlockedException.class)
    ResponseEntity<ProblemDetail> handleCustomerBlocked(CustomerBlockedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "customer-blocked", "Customer Blocked", ex.getMessage());
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ProblemDetail> handleCustomerNotFound(CustomerNotFoundException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "customer-not-found", "Customer Not Found", ex.getMessage());
    }

    @ExceptionHandler(ActiveOrderExistsException.class)
    ResponseEntity<ProblemDetail> handleActiveOrderExists(ActiveOrderExistsException ex) {
        return problem(HttpStatus.CONFLICT, "active-order-exists", "Active Order Exists", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "order-not-found", "Order Not Found", ex.getMessage());
    }

    @ExceptionHandler(OrderItemNotFoundException.class)
    ResponseEntity<ProblemDetail> handleItemNotFound(OrderItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "item-not-found", "Item Not Found", ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePaymentNotFound(PaymentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "payment-not-found", "Payment Not Found", ex.getMessage());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProductUnavailable(ProductUnavailableException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "product-unavailable", "Product Unavailable", ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ProblemDetail> handleProductNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "product-not-found", "Product Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ProblemDetail> handleInvalidState(InvalidStateTransitionException ex) {
        var status = STATE_STATUSES.getOrDefault(ex.getErrorCode(), HttpStatus.CONFLICT);
        return problem(status, ex.getErrorCode(), toTitle(ex.getErrorCode()), ex.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    ResponseEntity<ProblemDetail> handleExternalService(ExternalServiceException ex) {
        log.warn("External service error: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "external-service-unavailable",
                "External Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "Insufficient scope or permissions");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "Concurrent Modification", "The resource was modified concurrently — please retry");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", detail);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
                        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error", "An unexpected error occurred");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail) {
        var pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(BASE_URI + type));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("correlationId", UUID.randomUUID().toString());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private String toTitle(String errorCode) {
        return switch (errorCode) {
            case "order-not-modifiable" -> "Order Not Modifiable";
            case "order-not-confirmable" -> "Order Not Confirmable";
            case "order-not-cancellable" -> "Order Not Cancellable";
            case "order-not-confirmed" -> "Order Not Confirmed";
            case "payment-already-pending" -> "Payment Already Pending";
            case "payment-attempts-exceeded" -> "Payment Attempts Exceeded";
            case "empty-order" -> "Empty Order";
            default -> "Business Rule Violation";
        };
    }
}
