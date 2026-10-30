package com.wherelee.etry.trash;

import com.wherelee.etry.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public ApiResponse<TrashService.PageView> list(@AuthenticationPrincipal Long userId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(trashService.list(userId, page, size));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        trashService.restore(userId, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> purge(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        trashService.purge(userId, id);
        return ApiResponse.ok();
    }
}
