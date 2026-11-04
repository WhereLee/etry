package com.wherelee.edu.admin;

import com.wherelee.edu.admin.dto.AssignSeriesRequest;
import com.wherelee.edu.admin.dto.CreateUserRequest;
import com.wherelee.edu.admin.dto.SeriesOptionVO;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserMapper mapper;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(jdbc, passwordEncoder, userRepository, mapper);
    }

    @Test
    void cannotDisableOwnAccount() {
        assertThatThrownBy(() -> service.changeStatus(1L, 1L, "DISABLED"))
                .isInstanceOf(BizException.class)
                .hasMessage("不能修改自己的账号状态");

        verifyNoInteractions(jdbc, userRepository, mapper);
    }

    @Test
    void cannotResetOwnPassword() {
        assertThatThrownBy(() -> service.resetPassword(1L, 1L, "NewPass123"))
                .isInstanceOf(BizException.class)
                .hasMessage("请通过修改密码修改自己的密码");

        verifyNoInteractions(jdbc, userRepository, mapper);
    }

    @Test
    void onlyStudentCanReceiveSeries() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "TEACHER", "ACTIVE", 0, false)));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L))))
                .isInstanceOf(BizException.class)
                .hasMessage("只有学员可以分配系列");

        verify(mapper, never()).deleteUserSeries(anyLong());
        verify(mapper, never()).insertUserSeries(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsUnknownSeriesBeforeReplacingExistingAssignments() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "STUDENT", "ACTIVE", 0, false)));
        when(mapper.findAllSeries()).thenReturn(List.of(new SeriesOptionVO(1L, "物理必修一")));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L, 99L))))
                .isInstanceOf(BizException.class)
                .hasMessage("包含不存在的系列");

        verify(mapper, never()).deleteUserSeries(anyLong());
    }

    @Test
    void disablingAccountBumpsTokenVersion() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "STUDENT", "ACTIVE", 0, false)));

        service.changeStatus(1L, 2L, "DISABLED");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(2L));
        assertThat(sql.getValue())
                .contains("status = 'DISABLED'")
                .contains("token_version = token_version + 1");
    }

    @Test
    void passwordResetRequiresAnotherChangeAndBumpsTokenVersion() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "STUDENT", "ACTIVE", 0, false)));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded");

        service.resetPassword(1L, 2L, "NewPass123");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("encoded"), eq(2L));
        assertThat(sql.getValue())
                .contains("must_change_password = 1")
                .contains("token_version = token_version + 1");
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.findByUsername("student1"))
                .thenReturn(Optional.of(user(9L, "STUDENT", "ACTIVE", 0, false)));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("student1", "学生一", "Pass1234", "STUDENT")))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");

        verifyNoInteractions(jdbc);
    }

    @Test
    void createRejectsNonStudentRole() {
        when(userRepository.findByUsername("teacher2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("teacher2", "教师二", "Pass1234", "TEACHER")))
                .isInstanceOf(BizException.class)
                .hasMessage("教师账号必须通过邀请注册");

        verifyNoInteractions(jdbc);
    }

    private User user(Long id, String role, String status, int tokenVersion, boolean mustChangePassword) {
        return new User(id, "user" + id, "hash", "用户" + id, role, status, mustChangePassword, tokenVersion);
    }
}
