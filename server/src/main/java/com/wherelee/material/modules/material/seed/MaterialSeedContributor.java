package com.wherelee.material.modules.material.seed;

import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import com.wherelee.material.modules.platform.seed.SeedContext;
import com.wherelee.material.modules.platform.seed.SeedContributor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示资料目录种子（PRD §1：演示资料由项目生成 PDF，不依赖外部采购）。
 * 仅在目录为空时写入，避免覆盖运营改动；文件本体由 MaterialFileStore 惰性生成。
 */
@Component
public class MaterialSeedContributor implements SeedContributor {

    private final MaterialRepository materialRepository;

    public MaterialSeedContributor(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public void contribute(SeedContext context) {
        if (materialRepository.countAdmin(null, null, null) > 0) {
            return;
        }
        List<MaterialRecord> demos = List.of(
                demo("高中数学函数专项突破试卷（含详解）", "EXAM_PAPER", "SENIOR", "数学",
                        "覆盖函数定义域、单调性、奇偶性与综合应用，共 12 套专项卷，逐题详解。",
                        990, 86, 3, "demo-math-exam.pdf", MaterialRecord.ON_SALE, true),
                demo("高中英语语法通关教辅", "TEACHING_AID", "SENIOR", "英语",
                        "时态、从句、非谓语动词三大板块系统梳理，配套随堂练习与答案解析。",
                        1590, 64, 3, "demo-english-aid.pdf", MaterialRecord.ON_SALE, false),
                demo("小学语文阅读提分讲义", "LECTURE_NOTES", "PRIMARY", "语文",
                        "记叙文与说明文阅读方法精讲，附答题模板与阶梯训练。",
                        690, 40, 2, "demo-chinese-notes.pdf", MaterialRecord.ON_SALE, false),
                demo("高中物理必修一电子课本（同步精编版）", "TEXTBOOK", "SENIOR", "物理",
                        "运动的描述、匀变速直线运动、相互作用与牛顿运动定律，同步精编注释版。",
                        1290, 120, 5, "demo-physics-book.pdf", MaterialRecord.ON_SALE, false),
                demo("初中化学实验安全手册（内部资料）", "LECTURE_NOTES", "JUNIOR", "化学",
                        "演示用下架商品：尚未挂载文件，不可上架售卖。",
                        490, 24, 2, null, MaterialRecord.OFF_SALE, false)
        );
        demos.forEach(materialRepository::insert);
    }

    private static MaterialRecord demo(String title, String category, String stage, String subject,
                                       String summary, long priceCents, int pageCount, int previewPages,
                                       String fileKey, String saleStatus, boolean featured) {
        return new MaterialRecord(null, title, category, stage, subject, summary, priceCents, "CNY",
                pageCount, previewPages, null, fileKey, saleStatus, null, null, null, featured);
    }
}