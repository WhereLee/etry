package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.course.api.CourseGovernance;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.identity.api.UserAccount;
import com.wherelee.material.modules.identity.api.UserAccountOperations;
import com.wherelee.material.modules.identity.api.UserAccountQuery;
import com.wherelee.material.modules.material.api.MaterialQuery;
import com.wherelee.material.modules.operations.admin.dto.AdminUserVO;
import com.wherelee.material.modules.operations.admin.dto.AssignSeriesRequest;
import com.wherelee.material.modules.operations.admin.dto.CreateUserRequest;
import com.wherelee.material.modules.operations.admin.dto.SeriesOptionVO;
import com.wherelee.material.modules.operations.admin.dto.UserEntitlementView;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final UserAccountQuery userAccountQuery;
    private final UserAccountOperations userAccounts;
    private final EntitlementCommand entitlementCommand;
    private final EntitlementQuery entitlementQuery;
    private final CourseGovernance courseGovernance;
    private final MaterialQuery materialQuery;

    public AdminUserService(UserAccountQuery userAccountQuery, UserAccountOperations userAccounts,
                            EntitlementCommand entitlementCommand, EntitlementQuery entitlementQuery,
                            CourseGovernance courseGovernance, MaterialQuery materialQuery) {
        this.userAccountQuery = userAccountQuery;
        this.userAccounts = userAccounts;
        this.entitlementCommand = entitlementCommand;
        this.entitlementQuery = entitlementQuery;
        this.courseGovernance = courseGovernance;
        this.materialQuery = materialQuery;
    }

    public PageResult<AdminUserVO> page(String keyword, String role, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = userAccountQuery.count(keyword, role, status);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<UserAccountQuery.UserRow> rows = userAccountQuery.page(keyword, role, status,
                (safePage - 1) * safeSize, safeSize);
        Map<Long, Long> seriesCounts = entitlementQuery.countActiveByUsers(
                rows.stream().map(UserAccountQuery.UserRow::id).toList(), ProductTypes.COURSE_SERIES);
        List<AdminUserVO> records = rows.stream().map(row -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(row.id());
            vo.setUsername(row.username());
            vo.setNickname(row.nickname());
            vo.setRole(row.role());
            vo.setStatus(row.status());
            vo.setMustChangePassword(row.mustChangePassword());
            vo.setSeriesCount(seriesCounts.getOrDefault(row.id(), 0L));
            vo.setCreatedAt(row.createdAt());
            return vo;
        }).toList();
        return new PageResult<>(records, total, safePage, safeSize);
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
        Set<Long> validIds = courseGovernance.publishedOptions().stream()
                .map(CourseGovernance.SeriesOption::id)
                .collect(Collectors.toSet());
        if (!validIds.containsAll(seriesIds)) {
            throw BizException.badRequest("包含不存在的系列");
        }
        entitlementCommand.replaceManual(userId, ProductTypes.COURSE_SERIES, seriesIds);
    }

    public List<Long> seriesIds(Long userId) {
        userAccounts.require(userId);
        return entitlementQuery.listManualProductIds(userId, ProductTypes.COURSE_SERIES);
    }

    public List<SeriesOptionVO> seriesOptions() {
        return courseGovernance.publishedOptions().stream()
                .map(option -> new SeriesOptionVO(option.id(), option.title()))
                .toList();
    }

    public List<UserEntitlementView> entitlements(Long userId) {
        userAccounts.require(userId);
        List<UserEntitlementView> views = new ArrayList<>();
        List<EntitlementQuery.ActiveProduct> materials = entitlementQuery.listActive(userId, ProductTypes.MATERIAL);
        Map<Long, String> materialTitles = materialQuery.findFacts(
                        materials.stream().map(EntitlementQuery.ActiveProduct::productId).toList())
                .stream().collect(Collectors.toMap(MaterialQuery.MaterialFact::materialId,
                        MaterialQuery.MaterialFact::title));
        for (EntitlementQuery.ActiveProduct product : materials) {
            views.add(new UserEntitlementView(ProductTypes.MATERIAL, product.productId(),
                    materialTitles.getOrDefault(product.productId(), "资料#" + product.productId()),
                    product.displayOrder()));
        }
        Map<Long, String> seriesTitles = courseGovernance.publishedOptions().stream()
                .collect(Collectors.toMap(CourseGovernance.SeriesOption::id, CourseGovernance.SeriesOption::title));
        for (EntitlementQuery.ActiveProduct product : entitlementQuery.listActive(userId, ProductTypes.COURSE_SERIES)) {
            views.add(new UserEntitlementView(ProductTypes.COURSE_SERIES, product.productId(),
                    seriesTitles.getOrDefault(product.productId(), "系列#" + product.productId()),
                    product.displayOrder()));
        }
        return views;
    }
}