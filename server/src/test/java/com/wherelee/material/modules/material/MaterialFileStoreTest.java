package com.wherelee.material.modules.material;

import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialFileStoreTest {

    @TempDir
    Path tempDir;

    private final MaterialPdfFactory factory = new MaterialPdfFactory();

    @Test
    void generatesDemoFileWhenMissingThenReadsFromDisk() throws IOException {
        MaterialFileStore store = new MaterialFileStore(new MaterialStorageProperties(tempDir.toString()), factory);
        MaterialRecord material = material("material/gen.pdf");

        byte[] first = store.loadOrGenerate(material);
        Path file = tempDir.resolve("material/gen.pdf");
        assertThat(file).exists();
        assertThat(Files.readAllBytes(file)).isEqualTo(first);

        Files.write(file, new byte[]{1, 2, 3});
        assertThat(store.loadOrGenerate(material)).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsPathTraversal() {
        MaterialFileStore store = new MaterialFileStore(new MaterialStorageProperties(tempDir.toString()), factory);
        assertThatThrownBy(() -> store.loadOrGenerate(material("../evil.pdf")))
                .isInstanceOf(BizException.class)
                .hasMessage("非法文件路径");
    }

    private MaterialRecord material(String fileKey) {
        LocalDateTime now = LocalDateTime.now();
        return new MaterialRecord(1L, "测试资料", "EXAM_PAPER", "SENIOR", "数学", "简介",
                990, "CNY", 2, 1, null, fileKey, MaterialRecord.ON_SALE, now, now, now, false);
    }
}