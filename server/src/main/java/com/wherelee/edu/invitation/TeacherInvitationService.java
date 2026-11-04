package com.wherelee.edu.invitation;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.invitation.dto.TeacherInvitationDtos;
import com.wherelee.edu.user.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TeacherInvitationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final TeacherInvitationMapper mapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvitationTokenService tokenService;
    private final InvitationProperties properties;

    public TeacherInvitationService(TeacherInvitationMapper mapper,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    InvitationTokenService tokenService,
                                    InvitationProperties properties) {
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Transactional
    public TeacherInvitationDtos.CreatedResponse create(TeacherInvitationDtos.CreateRequest request, Long adminId) {
        String username = request.username().trim();
        String nickname = request.nickname().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.findPendingByUsername(username, now) != null) {
            throw BizException.badRequest("该用户名已有未过期的教师邀请");
        }

        String token = tokenService.generate();
        LocalDateTime expiresAt = now.plusHours(properties.safeExpiryHours());
        TeacherInvitation invitation = new TeacherInvitation();
        invitation.setTokenHash(tokenService.hash(token));
        invitation.setUsername(username);
        invitation.setNickname(nickname);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(expiresAt);
        invitation.setCreatedBy(adminId);
        mapper.insert(invitation);

        return new TeacherInvitationDtos.CreatedResponse(
                invitation.getId(), username, nickname,
                properties.normalizedBaseUrl() + "/invite/" + token, expiresAt);
    }

    public PageResult<TeacherInvitationVO> page(String status, int page, int size) {
        int safePage = Math.min(Math.max(1, page), MAX_PAGE);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        long total = mapper.count(normalizedStatus);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        return new PageResult<>(
                mapper.findPage(normalizedStatus, LocalDateTime.now(),
                        (safePage - 1) * safeSize, safeSize),
                total, safePage, safeSize);
    }

    @Transactional
    public void revoke(Long id) {
        TeacherInvitation invitation = mapper.findById(id);
        if (invitation == null) {
            throw BizException.notFound("邀请不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!"PENDING".equals(invitation.getStatus()) || !invitation.getExpiresAt().isAfter(now)) {
            throw BizException.badRequest("邀请已失效，不能撤销");
        }
        if (mapper.revoke(id, now) != 1) {
            throw BizException.badRequest("邀请状态已变化，请刷新后重试");
        }
    }

    public TeacherInvitationDtos.PreviewResponse preview(String token) {
        TeacherInvitation invitation = findByToken(token);
        requireUsable(invitation);
        return new TeacherInvitationDtos.PreviewResponse(
                invitation.getUsername(), invitation.getNickname(), invitation.getExpiresAt());
    }

    @Transactional
    public Long accept(TeacherInvitationDtos.AcceptRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw BizException.badRequest("两次输入的密码不一致");
        }
        String tokenHash = tokenService.hash(request.token().trim());
        TeacherInvitation invitation = mapper.findByTokenHashForUpdate(tokenHash);
        requireUsable(invitation);
        if (userRepository.findByUsername(invitation.getUsername()).isPresent()) {
            throw BizException.badRequest("用户名已被占用");
        }
        Long userId;
        try {
            userId = userRepository.create(
                    invitation.getUsername(),
                    passwordEncoder.encode(request.password()),
                    invitation.getNickname(),
                    "TEACHER",
                    false);
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("用户名已被占用");
        }
        if (mapper.markUsed(invitation.getId(), userId, LocalDateTime.now()) != 1) {
            throw BizException.badRequest("邀请已失效，请重新获取");
        }
        return userId;
    }

    private TeacherInvitation findByToken(String token) {
        return mapper.findByTokenHash(tokenService.hash(token.trim()));
    }

    private void requireUsable(TeacherInvitation invitation) {
        if (invitation == null
                || !"PENDING".equals(invitation.getStatus())
                || !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("邀请无效或已过期");
        }
    }
}
