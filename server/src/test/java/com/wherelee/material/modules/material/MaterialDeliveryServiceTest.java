package com.wherelee.material.modules.material;

import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.identity.api.UserDirectoryQuery;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialDeliveryServiceTest {

    @Mock private MaterialRepository materialRepository;
    @Mock private MaterialFileStore fileStore;
    @Mock private MaterialPdfFactory pdfFactory;
    @Mock private EntitlementQuery entitlementQuery;
    @Mock private UserDirectoryQuery userDirectoryQuery;

    private MaterialDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new MaterialDeliveryService(materialRepository, fileStore, pdfFactory,
                entitlementQuery, userDirectoryQuery);
    }

    @Test
    void previewRejectsOffSaleMaterial() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(MaterialRecord.OFF_SALE, "material/a.pdf")));

        assertThatThrownBy(() -> service.preview(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("资料不存在或已下架");
    }

    @Test
    void previewRejectsMissingFile() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(MaterialRecord.ON_SALE, null)));

        assertThatThrownBy(() -> service.preview(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("资料文件尚未上传");
    }

    @Test
    void previewTruncatesToPreviewPages() {
        MaterialRecord material = material(MaterialRecord.ON_SALE, "material/a.pdf");
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material));
        when(fileStore.loadOrGenerate(material)).thenReturn(new byte[]{9});
        when(pdfFactory.preview(any(), eq(3))).thenReturn(new byte[]{8});

        assertThat(service.preview(1L)).containsExactly(8);
        verify(pdfFactory).preview(any(), eq(3));
    }

    @Test
    void downloadRequiresActiveEntitlement() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(MaterialRecord.ON_SALE, "material/a.pdf")));
        when(entitlementQuery.hasActive(7L, 1L, ProductTypes.MATERIAL)).thenReturn(false);

        assertThatThrownBy(() -> service.download(7L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("尚未购买该资料，无法下载");
        verify(fileStore, never()).loadOrGenerate(any());
    }

    @Test
    void downloadStampsBuyerWatermarkAndNamesFile() {
        MaterialRecord material = material(MaterialRecord.ON_SALE, "material/a.pdf");
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material));
        when(entitlementQuery.hasActive(7L, 1L, ProductTypes.MATERIAL)).thenReturn(true);
        when(userDirectoryQuery.nicknamesById(List.of(7L))).thenReturn(Map.of(7L, "张三"));
        when(fileStore.loadOrGenerate(material)).thenReturn(new byte[]{9});
        when(pdfFactory.stampBuyer(any(), anyString())).thenReturn(new byte[]{8});

        MaterialDeliveryService.Download download = service.download(7L, 1L);

        assertThat(download.content()).containsExactly(8);
        assertThat(download.filename()).isEqualTo("测试资料.pdf");
        verify(pdfFactory).stampBuyer(any(), eq("张三 · ID7"));
    }

    @Test
    void downloadAllowedForOffSaleMaterialWithEntitlement() {
        MaterialRecord material = material(MaterialRecord.OFF_SALE, "material/a.pdf");
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material));
        when(entitlementQuery.hasActive(7L, 1L, ProductTypes.MATERIAL)).thenReturn(true);
        when(userDirectoryQuery.nicknamesById(List.of(7L))).thenReturn(Map.of());
        when(fileStore.loadOrGenerate(material)).thenReturn(new byte[]{9});
        when(pdfFactory.stampBuyer(any(), anyString())).thenReturn(new byte[]{8});

        service.download(7L, 1L);

        verify(pdfFactory).stampBuyer(any(), eq("用户7 · ID7"));
    }

    @Test
    void libraryJoinsEntitlementsWithCatalogAndSkipsDeleted() {
        when(entitlementQuery.listActive(7L, ProductTypes.MATERIAL)).thenReturn(List.of(
                new EntitlementQuery.ActiveProduct(1L, ProductTypes.MATERIAL, 1),
                new EntitlementQuery.ActiveProduct(99L, ProductTypes.MATERIAL, 2)));
        when(materialRepository.findByIds(List.of(1L, 99L))).thenReturn(List.of(material(MaterialRecord.ON_SALE, "material/a.pdf")));

        List<MaterialDtos.LibraryItemView> library = service.library(7L);

        assertThat(library).hasSize(1);
        assertThat(library.get(0).title()).isEqualTo("测试资料");
        assertThat(library.get(0).downloadable()).isTrue();
    }

    @Test
    void libraryMarksMissingFileNotDownloadable() {
        when(entitlementQuery.listActive(7L, ProductTypes.MATERIAL)).thenReturn(List.of(
                new EntitlementQuery.ActiveProduct(1L, ProductTypes.MATERIAL, 1)));
        when(materialRepository.findByIds(List.of(1L))).thenReturn(List.of(material(MaterialRecord.ON_SALE, null)));

        assertThat(service.library(7L).get(0).downloadable()).isFalse();
    }

    private MaterialRecord material(String saleStatus, String fileKey) {
        LocalDateTime now = LocalDateTime.now();
        return new MaterialRecord(1L, "测试资料", "EXAM_PAPER", "SENIOR", "数学", "简介",
                990, "CNY", 6, 3, null, fileKey, saleStatus, now, now, now, false);
    }
}