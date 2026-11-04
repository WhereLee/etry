package com.wherelee.edu.admin;

import com.wherelee.edu.admin.dto.AdminUserVO;
import com.wherelee.edu.admin.dto.AssignSeriesRequest;
import com.wherelee.edu.admin.dto.CreateUserRequest;
import com.wherelee.edu.admin.dto.SeriesOptionVO;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AdminUserMapper mapper;

    public AdminUserService(JdbcTemplate jdbc,
                            PasswordEncoder passwordEncoder,
                            UserRepository userRepository,
                            AdminUserMapper mapper) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public PageResult<AdminUserVO> page(String keyword, String role, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = mapper.countUsers(keyword, role, status);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        return new PageResult<>(
                mapper.findPage(keyword, role, status, (safePage - 1) * safeSize, safeSize),
                total, safePage, safeSize);
    }

    @Transactional
    public Long create(CreateUserRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }
        if (!"STUDENT".equals(request.role())) {
            throw BizException.badRequest("教师账号必须通过邀请注册");
        }
        return userRepository.create(
                request.username(),
                passwordEncoder.encode(request.initialPassword()),
                request.nickname(),
                request.role(),
                true);
    }

    @Transactional
    public void changeStatus(Long operatorId, Long userId, String status) {
        if (operatorId.equals(userId)) {
            throw BizException.badRequest("不能修改自己的账号状态");
        }
        User target = requireUser(userId);
        if (status.equals(target.status())) {
            return;
        }
        if ("DISABLED".equals(status)) {
            jdbc.update("""
                    UPDATE t_user SET status = 'DISABLED', token_version = token_version + 1,
                                      updated_at = CURRENT_TIMESTAMP(3)
                    WHERE id = ?
                    """, userId);
        } else if ("ACTIVE".equals(status)) {
            jdbc.update("UPDATE t_user SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?", userId);
        } else {
            throw BizException.badRequest("状态只能是 ACTIVE 或 DISABLED");
        }
    }

    @Transactional
    public void resetPassword(Long operatorId, Long userId, String newPassword) {
        if (operatorId.equals(userId)) {
            throw BizException.badRequest("请通过修改密码修改自己的密码");
        }
        requireUser(userId);
        jdbc.update("""
                UPDATE t_user
                SET password_hash = ?, must_change_password = 1,
                    token_version = token_version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, passwordEncoder.encode(newPassword), userId);
    }

    @Transactional
    public void assignSeries(Long userId, AssignSeriesRequest request) {
        User target = requireUser(userId);
        if (!"STUDENT".equals(target.role())) {
            throw BizException.badRequest("只有学员可以分配系列");
        }
        List<Long> seriesIds = request.seriesIds() == null ? List.of() : request.seriesIds().stream().distinct().toList();
        Set<Long> validIds = mapper.findAllSeries().stream()
                .map(SeriesOptionVO::id)
                .collect(Collectors.toSet());
        if (!validIds.containsAll(seriesIds)) {
            throw BizException.badRequest("包含不存在的系列");
        }
        mapper.deleteUserSeries(userId);
        int sort = 1;
        for (Long seriesId : seriesIds) {
            mapper.insertUserSeries(userId, seriesId, sort++);
        }
    }

    public List<Long> seriesIds(Long userId) {
        requireUser(userId);
        return mapper.findSeriesIds(userId);
    }

    public List<SeriesOptionVO> seriesOptions() {
        return mapper.findAllSeries();
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }
}
