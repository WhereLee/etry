package com.wherelee.edu.auth.dto;

public record LoginResponse(String token, UserInfo user, boolean mustChangePassword) {
}