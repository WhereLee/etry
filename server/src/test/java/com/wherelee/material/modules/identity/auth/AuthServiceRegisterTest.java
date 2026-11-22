package com.wherelee.material.modules.identity.auth;

import com.wherelee.material.modules.identity.auth.dto.LoginResponse;
import com.wherelee.material.modules.identity.auth.dto.RegisterRequest;
import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.identity.security.JwtService;
import com.wherelee.material.modules.identity.user.User;
import com.wherelee.material.modules.identity.user.UserRepository;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRegisterTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RbacRepository rbacRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        rbacRepository = mock(RbacRepository.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, rbacRepository);
    }

    private User buyer(long id, String username, String nickname) {
        return new User(id, username, "hash", nickname, "STUDENT", "ACTIVE", false, 0);
    }

    @Test
    void registerCreatesBuyerAndReturnsToken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Alice#2026x")).thenReturn("hash");
        when(userRepository.create("alice", "hash", "爱丽丝", "STUDENT", false)).thenReturn(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(buyer(42L, "alice", "爱丽丝")));
        when(jwtService.createToken(any(User.class))).thenReturn("token-42");

        LoginResponse response = authService.register(new RegisterRequest("alice", "Alice#2026x", "爱丽丝"));

        assertThat(response.token()).isEqualTo("token-42");
        assertThat(response.mustChangePassword()).isFalse();
        assertThat(response.user().id()).isEqualTo(42L);
        assertThat(response.user().role()).isEqualTo("STUDENT");
        verify(rbacRepository).grantRole(42L, "BUYER", null);
    }

    @Test
    void registerTrimsUsernameAndDefaultsBlankNickname() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Bob#2026xyz")).thenReturn("hash");
        when(userRepository.create("bob", "hash", "bob", "STUDENT", false)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(buyer(7L, "bob", "bob")));
        when(jwtService.createToken(any(User.class))).thenReturn("token-7");

        LoginResponse response = authService.register(new RegisterRequest("  bob  ", "Bob#2026xyz", "   "));

        assertThat(response.user().nickname()).isEqualTo("bob");
        verify(userRepository).create("bob", "hash", "bob", "STUDENT", false);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(buyer(1L, "alice", "爱丽丝")));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "Alice#2026x", null)))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");
        verify(userRepository, never()).create(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(rbacRepository, never()).grantRole(org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }
}