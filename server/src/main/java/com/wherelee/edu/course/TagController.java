package com.wherelee.edu.course;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.course.dto.TagDtos;
import com.wherelee.edu.rbac.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程标签。列表公开（前台筛选用），增删改需要 tag:manage。
 */
@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/api/tags")
    public ApiResponse<List<TagDtos.TagVO>> list() {
        return ApiResponse.ok(tagService.list());
    }

    @PostMapping("/api/admin/tags")
    @RequirePermission("tag:manage")
    @AuditLog(action = "TAG_CREATE", targetType = "TAG")
    public ApiResponse<TagDtos.TagVO> create(@Valid @RequestBody TagDtos.SaveRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @PutMapping("/api/admin/tags/{id}")
    @RequirePermission("tag:manage")
    @AuditLog(action = "TAG_UPDATE", targetType = "TAG", targetIdIndex = 0)
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody TagDtos.SaveRequest request) {
        tagService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/admin/tags/{id}")
    @RequirePermission("tag:manage")
    @AuditLog(action = "TAG_DELETE", targetType = "TAG", targetIdIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}
