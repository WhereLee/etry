package com.wherelee.etry.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地存储的模拟端点：接收"直传"并回放下载。
 * 只在 etry.storage.type=local 时存在，生产环境不会注册。
 */
@RestController
@RequestMapping("/local-storage")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "etry.storage.type", havingValue = "local")
public class LocalStorageController {

    private final LocalStorage localStorage;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestParam("key") String key,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        Path target = localStorage.resolve(key);
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("key") String key,
                                             @RequestParam(value = "name", required = false) String name) {
        Path target = localStorage.resolve(key);
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        String fileName = name == null || name.isBlank() ? target.getFileName().toString() : name;
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(target));
    }
}
