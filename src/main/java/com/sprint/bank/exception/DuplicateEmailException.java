package com.sprint.bank.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("An account with that email address already exists.");
    }

    public DuplicateEmailException(String message) {
        super(message);
    }
}
