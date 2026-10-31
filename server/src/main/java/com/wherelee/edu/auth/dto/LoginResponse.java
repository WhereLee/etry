package com.wherelee.edu.auth.dto;

import java.util.List;

public record LoginResponse(String token, Long userId, String username, String nickname,
                            List<String> roles, boolean mustChangePassword) {
}
