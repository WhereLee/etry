package com.wherelee.edu.user;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.Keywords;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.teacher.TeacherService;
import com.wherelee.edu.user.dto.CreateUserRequest;
import com.wherelee.edu.user.dto.UserListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    /** 页码硬上限：offset 由 (page-1)*size 算，不封顶会整数溢出成负数，直接拼出非法 SQL */
    private static final int MAX_PAGE = 10_000;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TeacherService teacherService;

    public PageResult<UserListItemVO> page(String keyword, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeKeyword = Keywords.escapeLike(keyword);

        long total = userMapper.countPage(safeKeyword, status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }

        List<User> users = userMapper.findPage(safeKeyword, status, (safePage - 1) * safeSize, safeSize);
        // total 不为 0 但这一页为空（翻过了末页）时也要提前返回：
        // 空列表会拼出 WHERE user_id IN () 的非法 SQL
        if (users.isEmpty()) {
            // 翻过了末页：records 为空，但 total 必须保留真实总数，
            // 用 PageResult.empty 会把 total 写成 0，前端分页会显示「共 0 条」
            return PageResult.of(List.of(), total, safePage, safeSize);
        }
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
     * 管理员开号。机构里的老师账号由教务统一开通，不走自助注册。
     *
     * 初始密码由管理员掌握，所以标记「必须改密」；
     * 如果选了讲师角色，必须同时提交档案，档案直接是 APPROVED。
     */
    @Transactional
    public Long createUser(CreateUserRequest request) {
        if (userMapper.findByUsername(request.username()) != null) {
            throw BizException.of(ErrorCode.USERNAME_EXISTS);
        }

        List<Role> allRoles = roleMapper.findAll();
        Map<Long, Role> roleMap = allRoles.stream().collect(Collectors.toMap(Role::getId, r -> r));
        for (Long roleId : request.roleIds()) {
            if (!roleMap.containsKey(roleId)) {
                throw new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在: " + roleId);
            }
        }

        Role teacherRole = roleMapper.findByCode(Role.CODE_TEACHER);
        boolean isTeacher = teacherRole != null && request.roleIds().contains(teacherRole.getId());
        if (isTeacher && request.teacherProfile() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "选择了讲师角色，必须填写讲师档案");
        }
        if (!isTeacher && request.teacherProfile() != null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有讲师角色才能填写讲师档案");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setNickname(request.nickname());
        user.setStatus(User.STATUS_ACTIVE);
        user.setTokenVersion(0);
        user.setMustChangePassword(true);
        userMapper.insert(user);

        for (Long roleId : request.roleIds()) {
            roleMapper.bindRole(user.getId(), roleId);
        }
        if (isTeacher) {
            teacherService.createApprovedProfile(user.getId(), request.teacherProfile());
        }

        log.info("管理员开号 userId={} username={} roles={} teacher={}",
                user.getId(), user.getUsername(), request.roleIds(), isTeacher);
        return user.getId();
    }

    /**
     * 启用 / 禁用账号。
     * 不允许把自己禁用——否则管理员一不小心就把自己锁在门外，且没有别的入口能救回来。
     * 也不允许禁用系统里最后一个可用管理员。
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
        if (User.STATUS_DISABLED.equals(status) && isActiveAdmin(user) && isLastActiveAdmin()) {
            throw BizException.of(ErrorCode.LAST_ADMIN);
        }
        userMapper.updateStatus(userId, status);
        if (User.STATUS_DISABLED.equals(status)) {
            // 禁用同时把令牌版本 +1：否则解禁后，禁用前签发的 token 在剩余有效期内仍然可用
            userMapper.bumpTokenVersion(userId);
        }
        log.info("账号状态变更 userId={} {} -> {} operator={}", userId, user.getStatus(), status, operatorId);
    }

    /**
     * 管理员重置他人密码。不需要旧密码，重置后该用户所有设备立即下线。
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.resetPasswordAndMarkChange(userId, passwordEncoder.encode(newPassword));
        log.info("管理员重置密码 userId={}", userId);
    }

    /**
     * 强制下线：只作废 token，不改密码。
     * 账号被盗时这是比「禁用整号」更轻的止血手段。
     */
    @Transactional
    public void kick(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.bumpTokenVersion(userId);
        log.info("强制下线 userId={}", userId);
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

        // 撤掉最后一个可用管理员的 ADMIN 角色，等同于把后台锁死
        Role adminRole = roleMapper.findByCode(Role.CODE_ADMIN);
        boolean removingAdmin = adminRole == null || !roleIds.contains(adminRole.getId());
        if (removingAdmin && isActiveAdmin(user) && isLastActiveAdmin()) {
            throw BizException.of(ErrorCode.LAST_ADMIN);
        }

        roleMapper.unbindAll(userId);
        for (Long roleId : roleIds) {
            roleMapper.bindRole(userId, roleId);
        }
        log.info("用户角色变更 userId={} roles={}", userId, roleIds);
    }

    private boolean isActiveAdmin(User user) {
        return user.isActive()
                && roleMapper.findCodesByUserId(user.getId()).contains(Role.CODE_ADMIN);
    }

    /**
     * 判断「干掉这一个之后还有没有别的管理员」。
     * 用加锁读而不是普通 count：两个管理员同时相互撤销时，
     * 普通读各自会看到 2 个而双双放行，最终 0 个可用管理员。
     */
    private boolean isLastActiveAdmin() {
        List<Long> activeAdmins = roleMapper.lockActiveUserIdsByRoleCode(Role.CODE_ADMIN);
        return activeAdmins.size() <= 1;
    }
}
