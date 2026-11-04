package com.wherelee.edu.admin;

import com.wherelee.edu.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeriesServiceTest {

    @Mock
    private AdminSeriesMapper mapper;

    private AdminSeriesService service;

    @BeforeEach
    void setUp() {
        service = new AdminSeriesService(mapper);
    }

    @Test
    void detailRejectsMissingSeries() {
        when(mapper.findHeader(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("系列不存在");
    }

    @Test
    void adminCanArchiveSeries() {
        AdminSeriesHeaderVO header = header("PUBLISHED");
        when(mapper.findHeader(1L)).thenReturn(header);

        service.updateStatus(1L, "ARCHIVED");

        verify(mapper).updateStatus(1L, "ARCHIVED");
    }

    @Test
    void sameStatusDoesNotWriteAgain() {
        when(mapper.findHeader(1L)).thenReturn(header("DRAFT"));

        service.updateStatus(1L, "DRAFT");

        verify(mapper, never()).updateStatus(1L, "DRAFT");
    }

    @Test
    void adminCannotPublishTeacherContent() {
        when(mapper.findHeader(1L)).thenReturn(header("DRAFT"));

        assertThatThrownBy(() -> service.updateStatus(1L, "PUBLISHED"))
                .isInstanceOf(BizException.class)
                .hasMessage("管理员只能下架或归档系列");
    }

    private AdminSeriesHeaderVO header(String status) {
        AdminSeriesHeaderVO header = new AdminSeriesHeaderVO();
        header.setId(1L);
        header.setTitle("物理必修一");
        header.setStatus(status);
        header.setTeacherId(2L);
        header.setTeacherName("物理老师");
        return header;
    }
}
