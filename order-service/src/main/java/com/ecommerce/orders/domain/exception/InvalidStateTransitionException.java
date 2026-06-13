package com.ecommerce.orders.domain.exception;

public class InvalidStateTransitionException extends DomainException {

    private final String errorCode;

    public InvalidStateTransitionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
