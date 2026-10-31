package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.QaDtos;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程问答。登录即可用。
 *
 * 路径分两组：提问和列表挂在课程下（/api/courses/{id}/questions），
 * 详情和回答挂在问题下（/api/questions/{id}）——回答时手里只有问题 id。
 */
@RestController
@RequiredArgsConstructor
public class CourseQaController {

    private final CourseQaService qaService;

    @PostMapping("/api/courses/{courseId}/questions")
    public ApiResponse<Long> ask(@CurrentUser Long userId,
                                 @PathVariable Long courseId,
                                 @Valid @RequestBody QaDtos.AskRequest request) {
        return ApiResponse.ok(qaService.ask(userId, courseId, request));
    }

    @GetMapping("/api/courses/{courseId}/questions")
    public ApiResponse<PageResult<QaDtos.QuestionVO>> page(@PathVariable Long courseId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(qaService.page(courseId, page, size));
    }

    @GetMapping("/api/questions/{id}")
    public ApiResponse<QaDtos.QuestionDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(qaService.detail(id));
    }

    @PostMapping("/api/questions/{id}/answers")
    public ApiResponse<Long> answer(@CurrentUser Long userId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody QaDtos.AnswerRequest request) {
        return ApiResponse.ok(qaService.answer(userId, id, request));
    }

    /** 标记已解决 / 取消标记。只有提问人和课程讲师有权限 */
    @PutMapping("/api/questions/{id}/resolve")
    public ApiResponse<Void> resolve(@CurrentUser Long userId,
                                     @PathVariable Long id,
                                     @RequestParam boolean resolved) {
        qaService.resolve(userId, id, resolved);
        return ApiResponse.ok();
    }
}
