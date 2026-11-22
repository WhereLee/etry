package com.wherelee.material.modules.material;

import com.wherelee.material.modules.platform.common.BizException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商城目录公开读接口：列表/搜索/详情，游客可访问（安全链 GET /api/materials/** 放行）。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialCatalogController {

    private static final int MAX_SIZE = 50;

    private final MaterialRepository materialRepository;
    private final MaterialDeliveryService deliveryService;

    public MaterialCatalogController(MaterialRepository materialRepository,
                                     MaterialDeliveryService deliveryService) {
        this.materialRepository = materialRepository;
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public MaterialDtos.PageView list(@RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "12") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        List<MaterialDtos.ListItemView> items = materialRepository
                .listOnSale(category, safeSize, (long) (safePage - 1) * safeSize).stream()
                .map(this::toListItem)
                .toList();
        return new MaterialDtos.PageView(items, materialRepository.countOnSale(category), safePage, safeSize);
    }

    @GetMapping("/search")
    public List<MaterialDtos.ListItemView> search(@RequestParam @NotBlank(message = "请输入搜索关键词") String keyword,
                                                  @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return materialRepository.search(keyword.trim(), safeSize).stream()
                .map(this::toListItem)
                .toList();
    }

    @GetMapping("/{id}")
    public MaterialDtos.DetailView detail(@PathVariable Long id) {
        MaterialRecord material = materialRepository.findById(id)
                .filter(m -> MaterialRecord.ON_SALE.equals(m.saleStatus()))
                .orElseThrow(() -> BizException.notFound("资料不存在或已下架"));
        return new MaterialDtos.DetailView(material.id(), material.title(), material.category(),
                material.stage(), material.subject(), material.summary(), material.priceCents(),
                material.currency(), material.pageCount(), material.previewPages(), material.coverKey());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(deliveryService.preview(id));
    }

    private MaterialDtos.ListItemView toListItem(MaterialRecord m) {
        return new MaterialDtos.ListItemView(m.id(), m.title(), m.category(), m.stage(), m.subject(),
                m.summary(), m.priceCents(), m.currency(), m.pageCount(), m.coverKey());
    }
}