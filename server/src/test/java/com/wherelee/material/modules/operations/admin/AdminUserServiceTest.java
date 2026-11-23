package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.course.api.CourseGovernance;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.identity.api.UserAccount;
import com.wherelee.material.modules.identity.api.UserAccountOperations;
import com.wherelee.material.modules.identity.api.UserAccountQuery;
import com.wherelee.material.modules.operations.admin.dto.AssignSeriesRequest;
import com.wherelee.material.modules.operations.admin.dto.CreateUserRequest;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserAccountQuery userAccountQuery;
    @Mock
    private UserAccountOperations userAccounts;
    @Mock
    private EntitlementCommand entitlementCommand;
    @Mock
    private EntitlementQuery entitlementQuery;
    @Mock
    private CourseGovernance courseGovernance;
    @Mock
    private com.wherelee.material.modules.material.api.MaterialQuery materialQuery;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userAccountQuery, userAccounts,
                entitlementCommand, entitlementQuery, courseGovernance, materialQuery);
    }

    @Test
    void onlyStudentCanReceiveSeries() {
        when(userAccounts.require(2L)).thenReturn(new UserAccount(2L, "teacher", "教师", "TEACHER", "ACTIVE"));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L))))
                .isInstanceOf(BizException.class)
                .hasMessage("只有学员可以分配系列");

        verify(entitlementCommand, never()).replaceManual(anyLong(), anyString(), any());
    }

    @Test
    void rejectsUnknownSeriesBeforeReplacingExistingAssignments() {
        when(userAccounts.require(2L)).thenReturn(new UserAccount(2L, "student", "学生", "STUDENT", "ACTIVE"));
        when(courseGovernance.publishedOptions()).thenReturn(List.of(new CourseGovernance.SeriesOption(1L, "物理必修一")));

        assertThatThrownBy(() -> service.assignSeries(2L, new AssignSeriesRequest(List.of(1L, 99L))))
                .isInstanceOf(BizException.class)
                .hasMessage("包含不存在的系列");

        verify(entitlementCommand, never()).replaceManual(anyLong(), anyString(), any());
    }

    @Test
    void createRejectsNonStudentRole() {
        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("teacher2", "教师二", "Pass1234", "TEACHER")))
                .isInstanceOf(BizException.class)
                .hasMessage("教师账号必须通过邀请注册");

        verifyNoInteractions(userAccountQuery, courseGovernance, userAccounts);
    }

    @Test
    void entitlementsMergesMaterialAndSeriesTitles() {
        when(userAccounts.require(2L)).thenReturn(new UserAccount(2L, "student", "学生", "STUDENT", "ACTIVE"));
        when(entitlementQuery.listActive(2L, com.wherelee.material.modules.entitlement.api.ProductTypes.MATERIAL))
                .thenReturn(List.of(new EntitlementQuery.ActiveProduct(7L, "MATERIAL", 1)));
        when(materialQuery.findFacts(List.of(7L))).thenReturn(List.of(
                new com.wherelee.material.modules.material.api.MaterialQuery.MaterialFact(
                        7L, "数学试卷", "EXAM_PAPER", 990, "CNY", "ON_SALE", null)));
        when(entitlementQuery.listActive(2L, com.wherelee.material.modules.entitlement.api.ProductTypes.COURSE_SERIES))
                .thenReturn(List.of(new EntitlementQuery.ActiveProduct(3L, "COURSE_SERIES", 2)));
        when(courseGovernance.publishedOptions())
                .thenReturn(List.of(new CourseGovernance.SeriesOption(3L, "物理必修一")));

        var views = service.entitlements(2L);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).productType()).isEqualTo("MATERIAL");
        assertThat(views.get(0).title()).isEqualTo("数学试卷");
        assertThat(views.get(1).productType()).isEqualTo("COURSE_SERIES");
        assertThat(views.get(1).title()).isEqualTo("物理必修一");
    }
}