package com.wherelee.material.modules.material;

import com.wherelee.material.modules.platform.common.BizException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 资料文件本地磁盘存储。fileKey 相对存储根解析，拒绝越界路径。
 * 演示环境文件缺失时按 PRD §1 由项目生成示例 PDF（临时文件 + 原子落盘，并发安全）。
 */
@Component
public class MaterialFileStore {

    private final MaterialStorageProperties properties;
    private final MaterialPdfFactory pdfFactory;

    public MaterialFileStore(MaterialStorageProperties properties, MaterialPdfFactory pdfFactory) {
        this.properties = properties;
        this.pdfFactory = pdfFactory;
    }

    public byte[] loadOrGenerate(MaterialRecord material) {
        Path path = resolve(material.fileKey());
        try {
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
            byte[] demo = pdfFactory.generateDemo(material);
            Files.createDirectories(path.getParent());
            Path tmp = Files.createTempFile(path.getParent(), "gen-", ".tmp");
            try {
                Files.write(tmp, demo);
                try {
                    Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException | AccessDeniedException e) {
                    // Windows 下目标已被并发线程落盘时 ATOMIC_MOVE 抛 AccessDeniedException，直接读已有文件
                    if (!Files.exists(path)) {
                        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("资料文件读取失败", e);
        }
    }

    private Path resolve(String fileKey) {
        Path root = Path.of(properties.root()).toAbsolutePath().normalize();
        Path path = root.resolve(fileKey).normalize();
        if (!path.startsWith(root)) {
            throw BizException.badRequest("非法文件路径");
        }
        return path;
    }
}