package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountOperationsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAccountOperationsService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountOperationsService(userRepository, passwordEncoder);
    }

    @Test
    void cannotDisableOwnAccount() {
        assertThatThrownBy(() -> service.changeStatus(1L, 1L, "DISABLED"))
                .isInstanceOf(BizException.class)
                .hasMessage("不能修改自己的账号状态");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void cannotResetOwnPassword() {
        assertThatThrownBy(() -> service.resetPassword(1L, 1L, "NewPass123"))
                .isInstanceOf(BizException.class)
                .hasMessage("请通过修改密码修改自己的密码");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.findByUsername("student1"))
                .thenReturn(Optional.of(user(9L, "STUDENT", "ACTIVE")));

        assertThatThrownBy(() -> service.createStudent("student1", "Pass1234", "学生一"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void createsStudentWithForcedPasswordChange() {
        when(userRepository.findByUsername("student1")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Pass1234")).thenReturn("encoded");
        when(userRepository.create("student1", "encoded", "学生一", "STUDENT", true)).thenReturn(10L);

        assertThat(service.createStudent("student1", "Pass1234", "学生一")).isEqualTo(10L);
        verify(userRepository).create("student1", "encoded", "学生一", "STUDENT", true);
    }

    @Test
    void disablingAccountBumpsTokenVersionThroughRepository() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "STUDENT", "ACTIVE")));

        service.changeStatus(1L, 2L, "DISABLED");

        verify(userRepository).disable(2L);
    }

    @Test
    void passwordResetRequiresAnotherChangeAndBumpsTokenVersion() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "STUDENT", "ACTIVE")));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded");

        service.resetPassword(1L, 2L, "NewPass123");

        verify(userRepository).resetPassword(2L, "encoded");
    }

    private User user(Long id, String role, String status) {
        return new User(id, "user" + id, "hash", "用户" + id, role, status, false, 0);
    }
}