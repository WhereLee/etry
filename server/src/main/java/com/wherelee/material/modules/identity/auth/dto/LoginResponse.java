package com.wherelee.material.modules.identity.auth.dto;

public record LoginResponse(String token, UserInfo user, boolean mustChangePassword) {
}