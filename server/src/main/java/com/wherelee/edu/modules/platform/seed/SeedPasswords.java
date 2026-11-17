package com.wherelee.edu.modules.platform.seed;

import java.security.SecureRandom;

/**
 * 种子初始密码生成。满足强密码规则：16 位、至少一个字母与一个数字，
 * 排除易混淆的 I/O/l/o/0/1，避免把固定口令写进仓库。
 */
public final class SeedPasswords {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private SeedPasswords() {
    }

    public static String generatePassword() {
        char[] chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        chars[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}