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
import java.util.concurrent.Semaphore;

/**
 * 资料文件本地磁盘存储。fileKey 相对存储根解析，拒绝越界路径。
 * 演示环境文件缺失时按 PRD §1 由项目生成示例 PDF（临时文件 + 原子落盘，并发安全）。
 */
@Component
public class MaterialFileStore {

    private final MaterialStorageProperties properties;
    private final MaterialPdfFactory pdfFactory;
    private final Semaphore previewPermits = new Semaphore(4);

    public MaterialFileStore(MaterialStorageProperties properties, MaterialPdfFactory pdfFactory) {
        this.properties = properties;
        this.pdfFactory = pdfFactory;
    }

    public byte[] loadOrGenerate(MaterialRecord material) {
        Path path = resolve(material.fileKey());
        try {
            if (!Files.exists(path)) {
                writeAtomically(path, pdfFactory.generateDemo(material));
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("资料文件读取失败", e);
        }
    }

    /**
     * 试读文件缓存：按「源文件 + 试读页数」落盘，限制同时生成的数量，避免高并发试读把堆打满。
     */
    public byte[] loadOrGeneratePreview(MaterialRecord material) {
        Path path = resolve(material.fileKey() + ".preview-" + material.previewPages() + ".pdf");
        try {
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
            previewPermits.acquire();
            try {
                if (!Files.exists(path)) {
                    writeAtomically(path, pdfFactory.preview(loadOrGenerate(material), material.previewPages()));
                }
                return Files.readAllBytes(path);
            } finally {
                previewPermits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试读文件生成等待被中断", e);
        } catch (IOException e) {
            throw new UncheckedIOException("试读文件读取失败", e);
        }
    }

    private void writeAtomically(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "gen-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException | AccessDeniedException e) {
                // 目标已被并发线程落盘：保留已有文件，直接读取
                if (!Files.exists(path)) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * 运营上传的 PDF 落盘，返回新 fileKey。仅接受 %PDF 魔数文件。
     */
    public String saveUploaded(long materialId, byte[] bytes) {
        if (bytes == null || bytes.length < 5
                || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
            throw BizException.badRequest("仅支持 PDF 文件");
        }
        String fileKey = "material/upload-" + materialId + "-" + System.nanoTime() + ".pdf";
        Path path = resolve(fileKey);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = Files.createTempFile(path.getParent(), "upload-", ".tmp");
            try {
                Files.write(tmp, bytes);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("资料文件保存失败", e);
        }
        return fileKey;
    }

    public void deleteUploaded(String fileKey) {
        if (fileKey == null || !fileKey.startsWith("material/upload-")) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("资料文件删除失败", e);
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