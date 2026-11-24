package com.wherelee.material.modules.identity.auth.dto;

import java.util.List;

public record UserInfo(Long id, String username, String nickname, String role,
                       List<String> roles, List<String> permissions) {
}
