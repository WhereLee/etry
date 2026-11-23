package com.wherelee.material.modules.material;

import com.wherelee.material.modules.material.MaterialAdminDtos.AdminItemView;
import com.wherelee.material.modules.material.MaterialAdminDtos.CreateRequest;
import com.wherelee.material.modules.material.MaterialAdminDtos.UpdateRequest;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 运营端资料管理（PRD §2 运营）。上架校验对齐 MaterialSellableCatalog 可售口径：
 * ON_SALE 必须已挂文件且价格 > 0。
 */
@Service
public class MaterialAdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;
    private static final Set<String> CATEGORIES = Set.of("EXAM_PAPER", "TEACHING_AID", "LECTURE_NOTES", "TEXTBOOK");
    private static final Set<String> STAGES = Set.of("PRIMARY", "JUNIOR", "SENIOR", "GENERAL");

    private final MaterialRepository materialRepository;
    private final MaterialFileStore fileStore;

    public MaterialAdminService(MaterialRepository materialRepository, MaterialFileStore fileStore) {
        this.materialRepository = materialRepository;
        this.fileStore = fileStore;
    }

    public PageResult<AdminItemView> page(String keyword, String category, String saleStatus, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        long offset = (long) (safePage - 1) * safeSize;
        List<AdminItemView> records = materialRepository.adminPage(keyword, category, saleStatus, safeSize, offset)
                .stream().map(MaterialAdminService::toView).toList();
        long total = materialRepository.countAdmin(keyword, category, saleStatus);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    @Transactional
    public AdminItemView create(CreateRequest request) {
        validate(request.category(), request.stage(), request.pageCount(), request.previewPages());
        MaterialRecord record = new MaterialRecord(
                null, request.title().strip(), request.category(), blankToNull(request.stage()),
                blankToNull(request.subject()), blankToNull(request.summary()),
                request.priceCents(), "CNY", request.pageCount(), request.previewPages(),
                null, null, MaterialRecord.OFF_SALE, null, null, null, false);
        long id = materialRepository.insert(record);
        return toView(require(id));
    }

    @Transactional
    public AdminItemView update(long id, UpdateRequest request) {
        MaterialRecord existing = require(id);
        validate(request.category(), request.stage(), request.pageCount(), request.previewPages());
        MaterialRecord updated = new MaterialRecord(
                id, request.title().strip(), request.category(), blankToNull(request.stage()),
                blankToNull(request.subject()), blankToNull(request.summary()),
                request.priceCents(), existing.currency(), request.pageCount(), request.previewPages(),
                existing.coverKey(), existing.fileKey(), existing.saleStatus(),
                existing.saleUpdatedAt(), existing.createdAt(), existing.updatedAt(), existing.featured());
        materialRepository.update(updated);
        return toView(require(id));
    }

    @Transactional
    public AdminItemView changeSaleStatus(long id, String saleStatus) {
        MaterialRecord existing = require(id);
        if (!MaterialRecord.ON_SALE.equals(saleStatus) && !MaterialRecord.OFF_SALE.equals(saleStatus)) {
            throw BizException.badRequest("非法上架状态");
        }
        if (MaterialRecord.ON_SALE.equals(saleStatus)
                && (existing.fileKey() == null || existing.fileKey().isBlank() || existing.priceCents() <= 0)) {
            throw BizException.badRequest("上架前必须已上传文件且价格大于 0");
        }
        materialRepository.updateSaleStatus(id, saleStatus);
        return toView(require(id));
    }

    @Transactional
    public AdminItemView setFeatured(long id, boolean featured) {
        require(id);
        materialRepository.setFeatured(id, featured);
        return toView(require(id));
    }

    @Transactional
    public AdminItemView upload(long id, byte[] bytes) {
        MaterialRecord existing = require(id);
        String newKey = fileStore.saveUploaded(id, bytes);
        materialRepository.updateFileKey(id, newKey);
        fileStore.deleteUploaded(existing.fileKey());
        return toView(require(id));
    }

    private MaterialRecord require(long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("资料不存在"));
    }

    private static void validate(String category, String stage, int pageCount, int previewPages) {
        if (!CATEGORIES.contains(category)) {
            throw BizException.badRequest("非法分类");
        }
        if (stage != null && !stage.isBlank() && !STAGES.contains(stage)) {
            throw BizException.badRequest("非法学段");
        }
        if (previewPages > pageCount) {
            throw BizException.badRequest("试读页数不能超过总页数");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static AdminItemView toView(MaterialRecord m) {
        return new AdminItemView(m.id(), m.title(), m.category(), m.stage(), m.subject(), m.summary(),
                m.priceCents(), m.currency(), m.pageCount(), m.previewPages(), m.coverKey(), m.fileKey(),
                m.saleStatus(), m.featured(), m.createdAt(), m.updatedAt());
    }
}