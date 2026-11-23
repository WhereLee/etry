package com.wherelee.material.modules.material;

import com.wherelee.material.modules.material.MaterialAdminDtos.AdminItemView;
import com.wherelee.material.modules.material.MaterialAdminDtos.CreateRequest;
import com.wherelee.material.modules.material.MaterialAdminDtos.FeaturedRequest;
import com.wherelee.material.modules.material.MaterialAdminDtos.SaleStatusRequest;
import com.wherelee.material.modules.material.MaterialAdminDtos.UpdateRequest;
import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 运营端资料管理接口（PRD §2 运营、§4 权限点鉴权 + 写操作审计）。
 */
@RestController
@RequestMapping("/api/admin/materials")
public class AdminMaterialController {

    private final MaterialAdminService service;

    public AdminMaterialController(MaterialAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('material:read')")
    @Measured("admin.material.page")
    public PageResult<AdminItemView> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String saleStatus,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.page(keyword, category, saleStatus, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('material:create')")
    @Measured("admin.material.create")
    @AdminAudit(action = "MATERIAL_CREATE", targetType = "MATERIAL", targetIdFromResult = true)
    public AdminItemView create(@Valid @RequestBody CreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('material:update')")
    @Measured("admin.material.update")
    @AdminAudit(action = "MATERIAL_UPDATE", targetType = "MATERIAL", targetIdArg = 0)
    public AdminItemView update(@PathVariable long id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/sale-status")
    @PreAuthorize("hasAuthority('material:sale')")
    @Measured("admin.material.sale")
    @AdminAudit(action = "MATERIAL_SALE_STATUS", targetType = "MATERIAL", targetIdArg = 0)
    public AdminItemView changeSaleStatus(@PathVariable long id, @Valid @RequestBody SaleStatusRequest request) {
        return service.changeSaleStatus(id, request.saleStatus());
    }

    @PutMapping("/{id}/featured")
    @PreAuthorize("hasAuthority('material:update')")
    @Measured("admin.material.featured")
    @AdminAudit(action = "MATERIAL_FEATURED", targetType = "MATERIAL", targetIdArg = 0)
    public AdminItemView setFeatured(@PathVariable long id, @Valid @RequestBody FeaturedRequest request) {
        return service.setFeatured(id, request.featured());
    }

    @PostMapping("/{id}/file")
    @PreAuthorize("hasAuthority('material:update')")
    @Measured("admin.material.upload")
    @AdminAudit(action = "MATERIAL_UPLOAD", targetType = "MATERIAL", targetIdArg = 0)
    public AdminItemView upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw BizException.badRequest("文件为空");
        }
        try {
            return service.upload(id, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("上传文件读取失败", e);
        }
    }
}