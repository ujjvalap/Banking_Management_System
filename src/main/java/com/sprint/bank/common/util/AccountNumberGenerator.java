package com.sprint.bank.common.util;

import java.security.SecureRandom;

public final class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "NB";

    private AccountNumberGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(14);
        sb.append(PREFIX);
        for (int i = 0; i < 12; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
