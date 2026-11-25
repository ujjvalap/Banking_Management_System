package com.sprint.bank.exception;

public class EmailDeliveryUnavailableException extends RuntimeException {
    public EmailDeliveryUnavailableException() {
        super("Email delivery is not configured.");
    }
}
