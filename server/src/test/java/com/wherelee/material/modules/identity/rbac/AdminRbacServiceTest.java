package com.wherelee.material.modules.identity.rbac;

import com.wherelee.material.modules.identity.user.User;
import com.wherelee.material.modules.identity.user.UserRepository;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRbacServiceTest {

    @Mock
    private RbacRepository rbacRepository;
    @Mock
    private UserRepository userRepository;

    private AdminRbacService service;

    @BeforeEach
    void setUp() {
        service = new AdminRbacService(rbacRepository, userRepository);
    }

    private static User user(long id) {
        return new User(id, "u" + id, "hash", "用户" + id, "STUDENT", "ACTIVE", false, 0);
    }

    private static RoleRecord role(String code) {
        return new RoleRecord(1L, code, code, RoleRecord.SCOPE_ALL, true, null, LocalDateTime.now());
    }

    @Test
    void cannotAssignOwnRoles() {
        assertThatThrownBy(() -> service.assignRoles(7L, 7L, List.of("BUYER")))
                .isInstanceOf(BizException.class)
                .hasMessage("不能修改自己的角色");

        verify(rbacRepository, never()).deleteUserRoles(anyLong());
        verify(rbacRepository, never()).grantRole(anyLong(), anyString(), anyLong());
    }

    @Test
    void missingUserThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoles(1L, 99L, List.of("BUYER")))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void unknownRoleRejectedBeforeAnyWrite() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(rbacRepository.findRoleByCode("BUYER")).thenReturn(Optional.of(role("BUYER")));
        when(rbacRepository.findRoleByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoles(1L, 2L, List.of("BUYER", "GHOST")))
                .isInstanceOf(BizException.class)
                .hasMessage("角色不存在：GHOST");

        verify(rbacRepository, never()).deleteUserRoles(anyLong());
        verify(rbacRepository, never()).grantRole(anyLong(), anyString(), anyLong());
    }

    @Test
    void replacesRolesWithDistinctCodes() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(rbacRepository.findRoleByCode("BUYER")).thenReturn(Optional.of(role("BUYER")));
        when(rbacRepository.findRoleByCode("CUSTOMER_SERVICE")).thenReturn(Optional.of(role("CUSTOMER_SERVICE")));
        when(rbacRepository.findRoleCodesByUser(2L)).thenReturn(List.of("BUYER", "CUSTOMER_SERVICE"));

        List<String> result = service.assignRoles(1L, 2L, List.of("BUYER", "CUSTOMER_SERVICE", "BUYER"));

        assertThat(result).containsExactly("BUYER", "CUSTOMER_SERVICE");
        verify(rbacRepository).deleteUserRoles(2L);
        verify(rbacRepository).grantRole(2L, "BUYER", 1L);
        verify(rbacRepository).grantRole(2L, "CUSTOMER_SERVICE", 1L);
    }

    @Test
    void emptyListRevokesAllRoles() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(rbacRepository.findRoleCodesByUser(2L)).thenReturn(List.of());

        assertThat(service.assignRoles(1L, 2L, List.of())).isEmpty();

        verify(rbacRepository).deleteUserRoles(2L);
        verify(rbacRepository, never()).grantRole(anyLong(), anyString(), anyLong());
    }

    @Test
    void rolesCatalogCarriesPermissionCodes() {
        when(rbacRepository.listRoles()).thenReturn(List.of(role("SUPER_ADMIN")));
        when(rbacRepository.findPermissionCodesByRole("SUPER_ADMIN"))
                .thenReturn(List.of("audit:read", "user:manage"));

        List<AdminRbacDtos.RoleView> roles = service.roles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).code()).isEqualTo("SUPER_ADMIN");
        assertThat(roles.get(0).permissions()).containsExactly("audit:read", "user:manage");
    }
}