package com.wherelee.trade.auth.dto;

public record LoginResponse(String token, Long userId, String nickname, String role) {
}
