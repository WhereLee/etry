package com.wherelee.edu.teacher;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.teacher.dto.SubjectVO;
import com.wherelee.edu.teacher.dto.TeacherApplyRequest;
import com.wherelee.edu.teacher.dto.TeacherProfileVO;
import com.wherelee.edu.user.Role;
import com.wherelee.edu.user.RoleMapper;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherService {

    private static final int MAX_PAGE_SIZE = 50;
    /** 页码硬上限，防止 (page-1)*size 溢出成负 offset */
    private static final int MAX_PAGE = 10_000;

    private final TeacherProfileMapper profileMapper;
    private final TeacherSubjectMapper teacherSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 提交讲师申请。一人一档：已经提交过的直接拒绝，让他去改档案。
     */
    @Transactional
    public TeacherProfileVO apply(Long userId, TeacherApplyRequest request) {
        if (profileMapper.findByUserId(userId) != null) {
            throw BizException.of(ErrorCode.TEACHER_PROFILE_EXISTS);
        }
        validateSubjects(request.subjectIds());

        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(userId);
        applyFields(profile, request);
        profile.setStatus(TeacherProfile.STATUS_PENDING);
        profileMapper.insert(profile);
        teacherSubjectMapper.batchInsert(profile.getId(), request.subjectIds());

        log.info("讲师申请提交 userId={} profileId={}", userId, profile.getId());
        return toVO(profile);
    }

    public TeacherProfileVO myProfile(Long userId) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            throw BizException.of(ErrorCode.TEACHER_NOT_FOUND);
        }
        return toVO(profile);
    }

    /**
     * 修改自己的档案。已通过的不允许直接改——否则等于绕过审核改身份信息。
     * 改完状态回到 PENDING，需要重新审。
     */
    @Transactional
    public void updateProfile(Long userId, TeacherApplyRequest request) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            throw BizException.of(ErrorCode.TEACHER_NOT_FOUND);
        }
        if (TeacherProfile.STATUS_APPROVED.equals(profile.getStatus())) {
            throw new BizException(ErrorCode.TEACHER_STATUS_INVALID, "已通过审核的档案不能直接修改");
        }
        validateSubjects(request.subjectIds());

        applyFields(profile, request);
        profile.setStatus(TeacherProfile.STATUS_PENDING);
        profileMapper.updateProfile(profile);

        teacherSubjectMapper.deleteByTeacherId(profile.getId());
        teacherSubjectMapper.batchInsert(profile.getId(), request.subjectIds());
        log.info("讲师档案修改 userId={} 重新进入待审", userId);
    }

    /**
     * 审核。通过时必须在同一个事务里给用户加 TEACHER 角色，
     * 否则会出现「档案通过了但发不了课」的不一致。
     */
    @Transactional
    public void review(Long profileId, boolean approved, String rejectReason, Long operatorId) {
        TeacherProfile profile = profileMapper.findById(profileId);
        if (profile == null) {
            throw BizException.of(ErrorCode.TEACHER_NOT_FOUND);
        }
        if (!TeacherProfile.STATUS_PENDING.equals(profile.getStatus())) {
            throw new BizException(ErrorCode.TEACHER_STATUS_INVALID, "只有待审核的档案才能审核");
        }

        if (approved) {
            profileMapper.updateStatus(profileId, TeacherProfile.STATUS_APPROVED, null);
            Role teacher = roleMapper.findByCode(Role.CODE_TEACHER);
            if (teacher == null) {
                throw new IllegalStateException("内置角色 TEACHER 不存在");
            }
            List<String> currentRoles = roleMapper.findCodesByUserId(profile.getUserId());
            if (!currentRoles.contains(Role.CODE_TEACHER)) {
                roleMapper.bindRole(profile.getUserId(), teacher.getId());
            }
            log.info("讲师审核通过 profileId={} userId={} operator={}", profileId, profile.getUserId(), operatorId);
        } else {
            profileMapper.updateStatus(profileId, TeacherProfile.STATUS_REJECTED, rejectReason);
            log.info("讲师审核驳回 profileId={} reason={} operator={}", profileId, rejectReason, operatorId);
        }
    }

    public PageResult<TeacherProfileVO> page(String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = profileMapper.countPage(status);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<TeacherProfileVO> records = profileMapper
                .findPage(status, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toVO).toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    /** 前台可见的讲师详情：只有审核通过的才对外 */
    public TeacherProfileVO publicDetail(Long profileId) {
        TeacherProfile profile = profileMapper.findById(profileId);
        if (profile == null || !TeacherProfile.STATUS_APPROVED.equals(profile.getStatus())) {
            throw BizException.of(ErrorCode.TEACHER_NOT_FOUND);
        }
        return toVO(profile);
    }

    private void applyFields(TeacherProfile profile, TeacherApplyRequest request) {
        profile.setRealName(request.realName());
        profile.setTitle(request.title());
        profile.setBio(request.bio());
        profile.setYears(request.years() == null ? 0 : request.years());
    }

    private void validateSubjects(List<Long> subjectIds) {
        for (Long id : subjectIds) {
            if (subjectMapper.findById(id) == null) {
                throw new BizException(ErrorCode.SUBJECT_NOT_FOUND, "学科不存在: " + id);
            }
        }
    }

    private TeacherProfileVO toVO(TeacherProfile profile) {
        User user = userMapper.findById(profile.getUserId());
        List<SubjectVO> subjects = subjectMapper.findByTeacherId(profile.getId())
                .stream().map(SubjectVO::from).toList();
        return new TeacherProfileVO(
                profile.getId(),
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                profile.getRealName(),
                profile.getTitle(),
                profile.getBio(),
                profile.getAvatarKey(),
                profile.getYears(),
                profile.getStatus(),
                profile.getRejectReason(),
                subjects,
                profile.getCreatedAt());
    }
}
