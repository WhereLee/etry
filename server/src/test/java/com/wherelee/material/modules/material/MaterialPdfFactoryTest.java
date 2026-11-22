package com.wherelee.material.modules.material;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialPdfFactoryTest {

    private final MaterialPdfFactory factory = new MaterialPdfFactory();

    @Test
    void demoPdfHasRequestedPagesAndTitle() throws IOException {
        byte[] pdf = factory.generateDemo(material(6, 3));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(6);
            assertThat(new PDFTextStripper().getText(doc)).contains("高中数学函数专项突破试卷");
        }
    }

    @Test
    void previewKeepsOnlyPreviewPagesAndStampsTrialWatermark() throws IOException {
        byte[] pdf = factory.preview(factory.generateDemo(material(6, 3)), 3);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("试读");
            assertThat(text).doesNotContain("第 4 页");
        }
    }

    @Test
    void previewNeverExceedsActualPages() throws IOException {
        byte[] pdf = factory.preview(factory.generateDemo(material(2, 5)), 5);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void buyerWatermarkCoversEveryPage() throws IOException {
        byte[] pdf = factory.stampBuyer(factory.generateDemo(material(4, 2)), "张三 · ID7");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(4);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= 4; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                // 旋转水印会被逐字拆行，去空白后再比对
                assertThat(stripper.getText(doc).replaceAll("\\s", "")).contains("已购·张三·ID7");
            }
        }
    }

    private MaterialRecord material(int pageCount, int previewPages) {
        LocalDateTime now = LocalDateTime.now();
        return new MaterialRecord(1L, "高中数学函数专项突破试卷", "EXAM_PAPER", "SENIOR", "数学",
                "覆盖函数定义域与单调性。", 990, "CNY", pageCount, previewPages, null,
                "material/unit.pdf", MaterialRecord.ON_SALE, now, now, now);
    }
}