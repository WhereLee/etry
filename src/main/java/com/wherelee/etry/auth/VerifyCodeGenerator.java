package com.wherelee.etry.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerifyCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }
}
