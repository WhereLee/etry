package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.identity.api.UserAccount;
import com.wherelee.edu.modules.identity.api.UserAccountOperations;
import com.wherelee.edu.modules.operations.admin.dto.AdminUserVO;
import com.wherelee.edu.modules.operations.admin.dto.AssignSeriesRequest;
import com.wherelee.edu.modules.operations.admin.dto.CreateUserRequest;
import com.wherelee.edu.modules.operations.admin.dto.SeriesOptionVO;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final AdminUserMapper mapper;
    private final UserAccountOperations userAccounts;

    public AdminUserService(AdminUserMapper mapper, UserAccountOperations userAccounts) {
        this.mapper = mapper;
        this.userAccounts = userAccounts;
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

    public Long create(CreateUserRequest request) {
        if (!"STUDENT".equals(request.role())) {
            throw BizException.badRequest("教师账号必须通过邀请注册");
        }
        return userAccounts.createStudent(
                request.username(),
                request.initialPassword(),
                request.nickname()
        );
    }

    public void changeStatus(Long operatorId, Long userId, String status) {
        userAccounts.changeStatus(operatorId, userId, status);
    }

    public void resetPassword(Long operatorId, Long userId, String newPassword) {
        userAccounts.resetPassword(operatorId, userId, newPassword);
    }

    @Transactional
    public void assignSeries(Long userId, AssignSeriesRequest request) {
        UserAccount target = userAccounts.require(userId);
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
        userAccounts.require(userId);
        return mapper.findSeriesIds(userId);
    }

    public List<SeriesOptionVO> seriesOptions() {
        return mapper.findAllSeries();
    }
}