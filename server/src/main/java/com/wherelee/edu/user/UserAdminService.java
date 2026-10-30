package com.wherelee.edu.user;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.user.dto.UserListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public PageResult<UserListItemVO> page(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = userMapper.countPage(keyword, status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }

        List<User> users = userMapper.findPage(keyword, status, (safePage - 1) * safeSize, safeSize);
        List<Long> userIds = users.stream().map(User::getId).toList();

        // 一次查完这批人的角色，再在内存里分组，避免逐个用户查一次（N+1）
        Map<Long, List<String>> roleMap = roleMapper.findRolesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleRow::userId,
                        Collectors.mapping(UserRoleRow::roleCode, Collectors.toList())));

        List<UserListItemVO> records = users.stream()
                .map(u -> new UserListItemVO(
                        u.getId(), u.getUsername(), u.getNickname(), u.getStatus(),
                        roleMap.getOrDefault(u.getId(), List.of()),
                        u.getCreatedAt()))
                .toList();

        return PageResult.of(records, total, safePage, safeSize);
    }

    /**
     * 启用 / 禁用账号。
     * 不允许把自己禁用——否则管理员一不小心就把自己锁在门外，且没有别的入口能救回来。
     */
    @Transactional
    public void changeStatus(Long userId, String status, Long operatorId) {
        if (userId.equals(operatorId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "不能修改自己的账号状态");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        if (status.equals(user.getStatus())) {
            return;
        }
        userMapper.updateStatus(userId, status);
        log.info("账号状态变更 userId={} {} -> {} operator={}", userId, user.getStatus(), status, operatorId);
    }

    /**
     * 重新分配角色：整体覆盖。清空 + 逐个绑定放在一个事务里，
     * 避免中途失败导致用户变成「没有任何角色」。
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }

        List<Role> allRoles = roleMapper.findAll();
        Map<Long, Role> roleMap = allRoles.stream().collect(Collectors.toMap(Role::getId, r -> r));
        for (Long roleId : roleIds) {
            if (!roleMap.containsKey(roleId)) {
                throw new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在: " + roleId);
            }
        }

        roleMapper.unbindAll(userId);
        for (Long roleId : roleIds) {
            roleMapper.bindRole(userId, roleId);
        }
        log.info("用户角色变更 userId={} roles={}", userId, roleIds);
    }
}
