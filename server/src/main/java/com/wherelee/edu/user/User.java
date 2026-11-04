package com.wherelee.edu.user;

public record User(
        Long id,
        String username,
        String passwordHash,
        String nickname,
        String role,
        String status,
        boolean mustChangePassword,
        int tokenVersion
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}