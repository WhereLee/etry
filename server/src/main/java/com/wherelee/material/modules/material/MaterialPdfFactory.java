package com.wherelee.material.modules.material;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 交付加工：演示资料生成（PRD §1 项目自产示例 PDF）、试读截页、水印叠加。
 * 中文字体使用随包 GB2312 子集（PDFBox 标准 14 字体不含 CJK）。
 */
@Component
public class MaterialPdfFactory {

    private static final String FONT_RESOURCE = "/fonts/material-cjk.ttf";
    private static final float MARGIN = 56f;

    public byte[] generateDemo(MaterialRecord material) {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font font = loadFont(doc);
            int pages = Math.max(1, material.pageCount());
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    if (i == 1) {
                        drawCover(cs, font, page, material);
                    } else {
                        drawBody(cs, font, page, material, i);
                    }
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("演示 PDF 生成失败", e);
        }
    }

    public byte[] preview(byte[] source, int previewPages) {
        try (PDDocument doc = Loader.loadPDF(source)) {
            int keep = Math.min(Math.max(1, previewPages), doc.getNumberOfPages());
            for (int i = doc.getNumberOfPages() - 1; i >= keep; i--) {
                doc.removePage(i);
            }
            stamp(doc, "试读");
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("试读 PDF 加工失败", e);
        }
    }

    public byte[] stampBuyer(byte[] source, String buyerLabel) {
        try (PDDocument doc = Loader.loadPDF(source)) {
            stamp(doc, "已购 · " + buyerLabel);
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("下载 PDF 加工失败", e);
        }
    }

    private void stamp(PDDocument doc, String text) throws IOException {
        PDType0Font font = loadFont(doc);
        float size = 42f;
        float width = font.getStringWidth(text) / 1000f * size;
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.16f);
        for (PDPage page : doc.getPages()) {
            PDRectangle box = page.getCropBox();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setGraphicsStateParameters(gs);
                cs.beginText();
                cs.setFont(font, size);
                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45),
                        box.getWidth() / 2f, box.getHeight() / 2f));
                cs.newLineAtOffset(-width / 2f, 0);
                cs.showText(text);
                cs.endText();
            }
        }
    }

    private void drawCover(PDPageContentStream cs, PDType0Font font, PDPage page, MaterialRecord m)
            throws IOException {
        float width = page.getCropBox().getWidth() - 2 * MARGIN;
        float y = page.getCropBox().getHeight() - 120;
        y = drawWrapped(cs, font, 22, m.title(), MARGIN, y, width, 30);
        y -= 18;
        drawLine(cs, font, 12, "分类：" + m.category() + "　学段：" + m.stage() + "　学科：" + m.subject(), MARGIN, y);
        y -= 20;
        if (m.summary() != null && !m.summary().isBlank()) {
            y = drawWrapped(cs, font, 12, m.summary(), MARGIN, y, width, 18);
        }
        y -= 24;
        drawLine(cs, font, 11, "演示资料 · 由项目生成，仅供学习演示", MARGIN, y);
        drawLine(cs, font, 11, "共 " + m.pageCount() + " 页 · 试读前 " + m.previewPages() + " 页", MARGIN, y - 18);
    }

    private void drawBody(PDPageContentStream cs, PDType0Font font, PDPage page, MaterialRecord m, int pageNo)
            throws IOException {
        float width = page.getCropBox().getWidth() - 2 * MARGIN;
        float y = page.getCropBox().getHeight() - 90;
        y = drawLine(cs, font, 16, m.title(), MARGIN, y);
        y -= 24;
        y = drawLine(cs, font, 12, "第 " + pageNo + " 页 · 示例内容", MARGIN, y);
        y -= 24;
        String[] paragraph = {
                "本页为演示内容，用于展示电子资料的排版与阅读体验。",
                "正式资料由运营上传完整文件后替换，页面结构保持一致。",
                "购买后可下载带购买者水印的完整文件，水印用于追溯分发来源。",
                "试读页面带有试读水印，仅包含资料的前几页内容。"
        };
        for (int block = 0; block < 3; block++) {
            for (String line : paragraph) {
                y = drawWrapped(cs, font, 12, line, MARGIN, y, width, 18);
                y -= 6;
            }
            y -= 12;
        }
        drawLine(cs, font, 10, "— " + pageNo + " —", page.getCropBox().getWidth() / 2f - 12, 60);
    }

    private float drawWrapped(PDPageContentStream cs, PDType0Font font, float size, String text,
                              float x, float y, float width, float lineHeight) throws IOException {
        for (String line : wrap(font, size, text, width)) {
            y = drawLine(cs, font, size, line, x, y);
            y -= lineHeight;
        }
        return y;
    }

    private float drawLine(PDPageContentStream cs, PDType0Font font, float size, String text,
                           float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y;
    }

    private List<String> wrap(PDType0Font font, float size, String text, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                continue;
            }
            String candidate = current.append(c).toString();
            if (font.getStringWidth(candidate) / 1000f * size > width && current.length() > 1) {
                current.setLength(current.length() - 1);
                lines.add(current.toString());
                current.setLength(0);
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private PDType0Font loadFont(PDDocument doc) throws IOException {
        try (InputStream in = MaterialPdfFactory.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("缺少中文字体资源：" + FONT_RESOURCE);
            }
            return PDType0Font.load(doc, in, true);
        }
    }

    private byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}