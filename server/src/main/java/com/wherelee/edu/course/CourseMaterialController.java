package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.course.dto.MaterialDtos;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课件资料。
 *
 * 老师侧：拿上传签名、登记、删除、看自己课程的资料（不限课程状态）。
 * 学员侧：看已上架课程的资料列表，拿预签名下载地址。
 */
@RestController
@RequiredArgsConstructor
public class CourseMaterialController {

    private final CourseMaterialService materialService;

    /** 取上传签名：PDF / Word / PPT / ZIP，单文件 50MB */
    @PostMapping("/api/teacher/materials/ticket")
    public ApiResponse<MaterialDtos.TicketVO> ticket(@CurrentUser Long userId,
                                                     @Valid @RequestBody MaterialDtos.TicketRequest request) {
        return ApiResponse.ok(materialService.ticket(userId, request));
    }

    /** 登记一条资料：文件已经直传完毕 */
    @PostMapping("/api/teacher/courses/{id}/materials")
    public ApiResponse<Long> add(@CurrentUser Long userId,
                                 @PathVariable Long id,
                                 @Valid @RequestBody MaterialDtos.SaveRequest request) {
        return ApiResponse.ok(materialService.add(userId, id, request));
    }

    @DeleteMapping("/api/teacher/materials/{id}")
    public ApiResponse<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        materialService.delete(userId, id);
        return ApiResponse.ok();
    }

    /** 老师看自己课程的资料，草稿课也能看 */
    @GetMapping("/api/teacher/courses/{id}/materials")
    public ApiResponse<List<MaterialDtos.MaterialVO>> listForTeacher(@CurrentUser Long userId,
                                                                     @PathVariable Long id) {
        return ApiResponse.ok(materialService.listForTeacher(userId, id));
    }

    /** 学员看已上架课程的资料；必须登录，资料不对外 */
    @GetMapping("/api/courses/{id}/materials")
    public ApiResponse<List<MaterialDtos.MaterialVO>> listForLearner(@PathVariable Long id) {
        return ApiResponse.ok(materialService.listForLearner(id));
    }
}
