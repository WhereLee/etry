package com.wherelee.material.modules.material;

import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.identity.api.UserDirectoryQuery;
import com.wherelee.material.modules.platform.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资料交付：游客试读（前 N 页 + 试读水印）、买家下载（权益门槛 + 购买者水印）、已购书架。
 * 下载只认权益不认在售状态：下架不影响已购用户下载。
 */
@Service
public class MaterialDeliveryService {

    private final MaterialRepository materialRepository;
    private final MaterialFileStore fileStore;
    private final MaterialPdfFactory pdfFactory;
    private final EntitlementQuery entitlementQuery;
    private final UserDirectoryQuery userDirectoryQuery;

    public MaterialDeliveryService(MaterialRepository materialRepository,
                                   MaterialFileStore fileStore,
                                   MaterialPdfFactory pdfFactory,
                                   EntitlementQuery entitlementQuery,
                                   UserDirectoryQuery userDirectoryQuery) {
        this.materialRepository = materialRepository;
        this.fileStore = fileStore;
        this.pdfFactory = pdfFactory;
        this.entitlementQuery = entitlementQuery;
        this.userDirectoryQuery = userDirectoryQuery;
    }

    public byte[] preview(Long materialId) {
        MaterialRecord material = materialRepository.findById(materialId)
                .filter(m -> MaterialRecord.ON_SALE.equals(m.saleStatus()))
                .orElseThrow(() -> BizException.notFound("资料不存在或已下架"));
        requireFile(material);
        return pdfFactory.preview(fileStore.loadOrGenerate(material), material.previewPages());
    }

    public Download download(Long userId, Long materialId) {
        MaterialRecord material = materialRepository.findById(materialId)
                .orElseThrow(() -> BizException.notFound("资料不存在"));
        if (!entitlementQuery.hasActive(userId, materialId, ProductTypes.MATERIAL)) {
            throw new BizException(HttpStatus.FORBIDDEN, "尚未购买该资料，无法下载");
        }
        requireFile(material);
        String nickname = userDirectoryQuery.nicknamesById(List.of(userId))
                .getOrDefault(userId, "用户" + userId);
        byte[] pdf = pdfFactory.stampBuyer(fileStore.loadOrGenerate(material), nickname + " · ID" + userId);
        return new Download(pdf, material.title() + ".pdf");
    }

    public record Download(byte[] content, String filename) {
    }

    public List<MaterialDtos.LibraryItemView> library(Long userId) {
        List<Long> ids = entitlementQuery.listActive(userId, ProductTypes.MATERIAL).stream()
                .map(EntitlementQuery.ActiveProduct::productId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, MaterialRecord> materials = materialRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(MaterialRecord::id, Function.identity()));
        return ids.stream()
                .map(materials::get)
                .filter(m -> m != null)
                .map(m -> new MaterialDtos.LibraryItemView(m.id(), m.title(), m.category(),
                        m.stage(), m.subject(), m.pageCount(), m.coverKey(), m.fileKey() != null))
                .toList();
    }

    private void requireFile(MaterialRecord material) {
        if (material.fileKey() == null || material.fileKey().isBlank()) {
            throw BizException.notFound("资料文件尚未上传");
        }
    }
}