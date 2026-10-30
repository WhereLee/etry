package com.wherelee.etry.file;

import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.file.dto.FileDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    public ApiResponse<FileDtos.PageView<FileDtos.FileView>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created") String sort,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(fileService.list(userId, page, size, sort, q));
    }

    @GetMapping("/pending")
    public ApiResponse<List<FileDtos.PendingFileView>> pending(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(fileService.listPending(userId));
    }

    @PostMapping("/presign")
    public ApiResponse<FileDtos.PresignResponse> presign(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody FileDtos.PresignRequest request) {
        return ApiResponse.ok(fileService.presign(userId, request.name(), request.sizeBytes()));
    }

    @PostMapping("/{id}/commit")
    public ApiResponse<FileDtos.FileView> commit(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(fileService.commit(userId, id));
    }

    @DeleteMapping("/{id}/pending")
    public ApiResponse<Void> cancelPending(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long id) {
        fileService.cancelPending(userId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/download-url")
    public ApiResponse<FileDtos.DownloadUrl> downloadUrl(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(new FileDtos.DownloadUrl(fileService.downloadUrl(userId, id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<FileDtos.FileView> rename(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody FileDtos.RenameRequest request) {
        return ApiResponse.ok(fileService.rename(userId, id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long id) {
        fileService.moveToTrash(userId, id);
        return ApiResponse.ok();
    }
}
