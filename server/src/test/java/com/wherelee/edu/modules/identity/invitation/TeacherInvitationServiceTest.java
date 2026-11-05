package com.wherelee.edu.modules.identity.invitation;

import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.identity.invitation.dto.TeacherInvitationDtos;
import com.wherelee.edu.modules.identity.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherInvitationServiceTest {

    @Mock
    private TeacherInvitationMapper mapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private InvitationTokenService tokenService;

    private TeacherInvitationService service;

    @BeforeEach
    void setUp() {
        InvitationProperties properties = new InvitationProperties(
                "https://learn.example.com/", 72, 20, 10);
        service = new TeacherInvitationService(
                mapper, userRepository, passwordEncoder, tokenService, properties);
    }

    @Test
    void createStoresOnlyTokenHashAndReturnsOneTimeLink() {
        when(userRepository.findByUsername("physics_teacher")).thenReturn(Optional.empty());
        when(mapper.findPendingByUsername(eq("physics_teacher"), any())).thenReturn(null);
        when(tokenService.generate()).thenReturn("plain-token");
        when(tokenService.hash("plain-token")).thenReturn("a".repeat(64));
        doAnswer(invocation -> {
            TeacherInvitation invitation = invocation.getArgument(0);
            invitation.setId(11L);
            return 1;
        }).when(mapper).insert(any(TeacherInvitation.class));

        TeacherInvitationDtos.CreatedResponse response = service.create(
                new TeacherInvitationDtos.CreateRequest("physics_teacher", "物理老师"), 1L);

        ArgumentCaptor<TeacherInvitation> captor = ArgumentCaptor.forClass(TeacherInvitation.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(captor.getValue().getTokenHash()).doesNotContain("plain-token");
        assertThat(response.inviteUrl()).isEqualTo("https://learn.example.com/invite/plain-token");
    }

    @Test
    void rejectsDuplicatePendingInvitation() {
        when(userRepository.findByUsername("physics_teacher")).thenReturn(Optional.empty());
        when(mapper.findPendingByUsername(eq("physics_teacher"), any()))
                .thenReturn(pendingInvitation(LocalDateTime.now().plusHours(1)));

        assertThatThrownBy(() -> service.create(
                new TeacherInvitationDtos.CreateRequest("physics_teacher", "物理老师"), 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该用户名已有未过期的教师邀请");
    }

    @Test
    void acceptCreatesTeacherWithSelfChosenPassword() {
        TeacherInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash")).thenReturn(invitation);
        when(userRepository.findByUsername("physics_teacher")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Teacher1234")).thenReturn("encoded");
        when(userRepository.create("physics_teacher", "encoded", "物理老师", "TEACHER", false))
                .thenReturn(9L);
        when(mapper.markUsed(eq(11L), eq(9L), any())).thenReturn(1);

        Long userId = service.accept(new TeacherInvitationDtos.AcceptRequest(
                "plain-token", "Teacher1234", "Teacher1234"));

        assertThat(userId).isEqualTo(9L);
        verify(userRepository).create("physics_teacher", "encoded", "物理老师", "TEACHER", false);
        verify(mapper).markUsed(eq(11L), eq(9L), any());
    }

    @Test
    void acceptRejectsExpiredInvitation() {
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash"))
                .thenReturn(pendingInvitation(LocalDateTime.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.accept(new TeacherInvitationDtos.AcceptRequest(
                "plain-token", "Teacher1234", "Teacher1234")))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请无效或已过期");

        verify(userRepository, never()).create(any(), any(), any(), any(), eq(false));
    }

    @Test
    void acceptRejectsUsedInvitation() {
        TeacherInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        invitation.setStatus("USED");
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash")).thenReturn(invitation);

        assertThatThrownBy(() -> service.accept(new TeacherInvitationDtos.AcceptRequest(
                "plain-token", "Teacher1234", "Teacher1234")))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请无效或已过期");
    }

    @Test
    void acceptRejectsPasswordMismatch() {
        assertThatThrownBy(() -> service.accept(new TeacherInvitationDtos.AcceptRequest(
                "plain-token", "Teacher1234", "Teacher5678")))
                .isInstanceOf(BizException.class)
                .hasMessage("两次输入的密码不一致");
    }

    @Test
    void revokeRejectsUsedInvitation() {
        TeacherInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        invitation.setStatus("USED");
        when(mapper.findById(11L)).thenReturn(invitation);

        assertThatThrownBy(() -> service.revoke(11L))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请已失效，不能撤销");
    }

    private TeacherInvitation pendingInvitation(LocalDateTime expiresAt) {
        TeacherInvitation invitation = new TeacherInvitation();
        invitation.setId(11L);
        invitation.setTokenHash("hash");
        invitation.setUsername("physics_teacher");
        invitation.setNickname("物理老师");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(expiresAt);
        invitation.setCreatedBy(1L);
        return invitation;
    }
}
