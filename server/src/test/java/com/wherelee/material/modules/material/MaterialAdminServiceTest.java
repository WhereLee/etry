package com.wherelee.material.modules.material;

import com.wherelee.material.modules.material.MaterialAdminDtos.CreateRequest;
import com.wherelee.material.modules.material.MaterialAdminDtos.UpdateRequest;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialAdminServiceTest {

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private MaterialFileStore fileStore;

    private MaterialAdminService service;

    @BeforeEach
    void setUp() {
        service = new MaterialAdminService(materialRepository, fileStore);
    }

    private static MaterialRecord material(Long id, String fileKey, long priceCents, String saleStatus) {
        return new MaterialRecord(id, "高中数学试卷", "EXAM_PAPER", "SENIOR", "数学", "简介",
                priceCents, "CNY", 86, 3, null, fileKey, saleStatus,
                null, LocalDateTime.now(), LocalDateTime.now(), false);
    }

    private static CreateRequest createRequest() {
        return new CreateRequest("新资料", "EXAM_PAPER", "SENIOR", "数学", "简介", 990, 86, 3);
    }

    @Test
    void createDefaultsToOffSaleWithoutFile() {
        when(materialRepository.insert(any())).thenReturn(9L);
        when(materialRepository.findById(9L)).thenReturn(Optional.of(material(9L, null, 990, "OFF_SALE")));

        var view = service.create(createRequest());

        ArgumentCaptor<MaterialRecord> captor = ArgumentCaptor.forClass(MaterialRecord.class);
        verify(materialRepository).insert(captor.capture());
        MaterialRecord inserted = captor.getValue();
        assertThat(inserted.saleStatus()).isEqualTo(MaterialRecord.OFF_SALE);
        assertThat(inserted.fileKey()).isNull();
        assertThat(inserted.featured()).isFalse();
        assertThat(inserted.currency()).isEqualTo("CNY");
        assertThat(view.id()).isEqualTo(9L);
    }

    @Test
    void createRejectsInvalidCategoryStageAndPreviewPages() {
        assertThatThrownBy(() -> service.create(new CreateRequest("t", "BAD", "SENIOR", null, null, 100, 10, 1)))
                .isInstanceOf(BizException.class).hasMessage("非法分类");
        assertThatThrownBy(() -> service.create(new CreateRequest("t", "EXAM_PAPER", "BAD", null, null, 100, 10, 1)))
                .isInstanceOf(BizException.class).hasMessage("非法学段");
        assertThatThrownBy(() -> service.create(new CreateRequest("t", "EXAM_PAPER", "SENIOR", null, null, 100, 10, 11)))
                .isInstanceOf(BizException.class).hasMessage("试读页数不能超过总页数");
        verify(materialRepository, never()).insert(any());
    }

    @Test
    void onSaleRequiresFileAndPositivePrice() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(1L, null, 990, "OFF_SALE")));
        assertThatThrownBy(() -> service.changeSaleStatus(1L, MaterialRecord.ON_SALE))
                .isInstanceOf(BizException.class).hasMessage("上架前必须已上传文件且价格大于 0");

        when(materialRepository.findById(2L)).thenReturn(Optional.of(material(2L, "material/a.pdf", 0, "OFF_SALE")));
        assertThatThrownBy(() -> service.changeSaleStatus(2L, MaterialRecord.ON_SALE))
                .isInstanceOf(BizException.class).hasMessage("上架前必须已上传文件且价格大于 0");
        verify(materialRepository, never()).updateSaleStatus(anyLong(), anyString());
    }

    @Test
    void rejectsUnknownSaleStatus() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(1L, "material/a.pdf", 990, "ON_SALE")));
        assertThatThrownBy(() -> service.changeSaleStatus(1L, "DELETED"))
                .isInstanceOf(BizException.class).hasMessage("非法上架状态");
    }

    @Test
    void offSaleAlwaysAllowedAndUpdatesStatus() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(material(1L, "material/a.pdf", 990, "ON_SALE")));
        service.changeSaleStatus(1L, MaterialRecord.OFF_SALE);
        verify(materialRepository).updateSaleStatus(1L, MaterialRecord.OFF_SALE);
    }

    @Test
    void updateKeepsFileSaleStatusAndFeatured() {
        MaterialRecord existing = new MaterialRecord(1L, "旧标题", "EXAM_PAPER", "SENIOR", "数学", "旧简介",
                990, "CNY", 86, 3, "cover", "material/a.pdf", "ON_SALE",
                null, LocalDateTime.now(), LocalDateTime.now(), true);
        when(materialRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, new UpdateRequest("新标题", "TEXTBOOK", "JUNIOR", "物理", "新简介", 1290, 120, 5));

        ArgumentCaptor<MaterialRecord> captor = ArgumentCaptor.forClass(MaterialRecord.class);
        verify(materialRepository).update(captor.capture());
        MaterialRecord updated = captor.getValue();
        assertThat(updated.title()).isEqualTo("新标题");
        assertThat(updated.priceCents()).isEqualTo(1290);
        assertThat(updated.fileKey()).isEqualTo("material/a.pdf");
        assertThat(updated.saleStatus()).isEqualTo("ON_SALE");
        assertThat(updated.featured()).isTrue();
        assertThat(updated.coverKey()).isEqualTo("cover");
    }

    @Test
    void uploadReplacesFileKeyAndCleansOldUploadedFile() {
        when(materialRepository.findById(1L))
                .thenReturn(Optional.of(material(1L, "material/upload-1-100.pdf", 990, "ON_SALE")));
        when(fileStore.saveUploaded(eq(1L), any())).thenReturn("material/upload-1-200.pdf");

        service.upload(1L, new byte[]{'%', 'P', 'D', 'F', '-'});

        verify(materialRepository).updateFileKey(1L, "material/upload-1-200.pdf");
        verify(fileStore).deleteUploaded("material/upload-1-100.pdf");
    }

    @Test
    void missingMaterialThrowsNotFound() {
        when(materialRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setFeatured(99L, true))
                .isInstanceOf(BizException.class).hasMessage("资料不存在");
        assertThatThrownBy(() -> service.changeSaleStatus(99L, MaterialRecord.OFF_SALE))
                .isInstanceOf(BizException.class).hasMessage("资料不存在");
    }

    @Test
    void pageClampsSizeAndPage() {
        when(materialRepository.adminPage(null, null, null, 50, 0)).thenReturn(List.of());
        when(materialRepository.countAdmin(null, null, null)).thenReturn(0L);

        PageResult<MaterialAdminDtos.AdminItemView> result = service.page(null, null, null, 0, 500);

        assertThat(result.size()).isEqualTo(50);
        assertThat(result.page()).isEqualTo(1);
        verify(materialRepository).adminPage(null, null, null, 50, 0);
    }
}