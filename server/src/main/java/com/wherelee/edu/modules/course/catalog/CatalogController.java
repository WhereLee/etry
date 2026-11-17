package com.wherelee.edu.modules.course.catalog;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商店目录入口：展示拼装归属 course 读模型门面，commerce 只提供交易事实端口。
 * 路由与响应形状保持 /api/catalog/series（/products 别名）不变。
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final StoreCatalogReadModel storeCatalog;

    public CatalogController(StoreCatalogReadModel storeCatalog) {
        this.storeCatalog = storeCatalog;
    }

    @GetMapping({"/series", "/products"})
    @RequireRole("STUDENT")
    @Measured("catalog.series")
    public List<CatalogItem> list(@RequestAttribute("userId") Long userId) {
        return storeCatalog.listForStudent(userId);
    }
}