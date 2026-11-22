package com.wherelee.material.modules.material;

import com.wherelee.material.modules.platform.aop.Measured;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 已购资料书架与下载（需登录）。下载另经权益门槛，见 MaterialDeliveryService。
 */
@RestController
@RequestMapping("/api/library/materials")
public class MaterialLibraryController {

    private final MaterialDeliveryService deliveryService;

    public MaterialLibraryController(MaterialDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @Measured("library.list")
    public List<MaterialDtos.LibraryItemView> list(@RequestAttribute("userId") Long userId) {
        return deliveryService.library(userId);
    }

    @GetMapping("/{id}/download")
    @Measured("library.download")
    public ResponseEntity<byte[]> download(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        MaterialDeliveryService.Download file = deliveryService.download(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}