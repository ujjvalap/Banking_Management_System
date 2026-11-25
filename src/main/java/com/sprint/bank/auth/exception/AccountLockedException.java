package com.sprint.bank.auth.exception;

public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account temporarily locked due to repeated failed login attempts. Try again later.");
    }
}
