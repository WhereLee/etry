package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.identity.api.UserAccount;
import com.wherelee.edu.modules.identity.api.UserAccountOperations;
import com.wherelee.edu.modules.operations.admin.dto.AssignSeriesRequest;
import com.wherelee.edu.modules.operations.admin.dto.CreateUserRequest;
import com.wherelee.edu.modules.operations.admin.dto.SeriesOptionVO;
import com.wherelee.edu.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserMapper mapper;
    @Mock
    private UserAccountOperations userAccounts;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(mapper, userAccounts);
    }

    @Test
    void onlyStudentCanReceiveSeries() {
        when(userAccounts.require(2L)).thenReturn(new UserAccount(2L, "teacher", "教师", "TEACHER", "ACTIVE"));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L))))
                .isInstanceOf(BizException.class)
                .hasMessage("只有学员可以分配系列");

        verify(mapper, never()).deleteUserSeries(anyLong());
        verify(mapper, never()).insertUserSeries(anyLong(), anyLong(), anyInt());
    }

    @Test
    void rejectsUnknownSeriesBeforeReplacingExistingAssignments() {
        when(userAccounts.require(2L)).thenReturn(new UserAccount(2L, "student", "学生", "STUDENT", "ACTIVE"));
        when(mapper.findAllSeries()).thenReturn(List.of(new SeriesOptionVO(1L, "物理必修一")));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L, 99L))))
                .isInstanceOf(BizException.class)
                .hasMessage("包含不存在的系列");

        verify(mapper, never()).deleteUserSeries(anyLong());
    }

    @Test
    void createRejectsNonStudentRole() {
        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("teacher2", "教师二", "Pass1234", "TEACHER")))
                .isInstanceOf(BizException.class)
                .hasMessage("教师账号必须通过邀请注册");

        verifyNoInteractions(mapper, userAccounts);
    }
}