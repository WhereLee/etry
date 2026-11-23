package com.wherelee.material.modules.operations.admin;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.wherelee.material.modules.commerce.api.AdminReconciliationOperations;
import com.wherelee.material.modules.commerce.api.AdminReconciliationOperations.BreakView;
import com.wherelee.material.modules.platform.common.PageResult;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务报表导出（PRD §2 财务，EasyExcel）。导出上限 10000 行，与管理端分页上限一致。
 */
@Service
public class AdminExportService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final int BREAK_PAGE_SIZE = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminOrderMapper orderMapper;
    private final AdminReconciliationOperations reconciliation;

    public AdminExportService(AdminOrderMapper orderMapper, AdminReconciliationOperations reconciliation) {
        this.orderMapper = orderMapper;
        this.reconciliation = reconciliation;
    }

    public void exportOrders(OutputStream out, String keyword, String status) {
        List<OrderExportRow> rows = orderMapper.findPage(blankToNull(keyword), blankToNull(status), 0, EXPORT_LIMIT)
                .stream().map(AdminExportService::toOrderRow).toList();
        EasyExcel.write(out, OrderExportRow.class).sheet("订单").doWrite(rows);
    }

    public void exportBreaks(OutputStream out, long runId) {
        List<BreakExportRow> rows = new ArrayList<>();
        int page = 1;
        while (rows.size() < EXPORT_LIMIT) {
            PageResult<BreakView> result = reconciliation.breaks(runId, null, page, BREAK_PAGE_SIZE);
            result.records().forEach(b -> rows.add(toBreakRow(b)));
            if (result.records().isEmpty() || rows.size() >= result.total()) {
                break;
            }
            page++;
        }
        EasyExcel.write(out, BreakExportRow.class).sheet("对账差异").doWrite(rows);
    }

    private static OrderExportRow toOrderRow(AdminOrderVO vo) {
        OrderExportRow row = new OrderExportRow();
        row.setOrderNo(vo.getOrderNo());
        row.setUsername(vo.getUsername());
        row.setNickname(vo.getNickname());
        row.setSeriesTitle(vo.getSeriesTitle());
        row.setPriceCents(vo.getPriceCents());
        row.setCurrency(vo.getCurrency());
        row.setStatus(vo.getStatus());
        row.setPayChannel(vo.getPayChannel());
        row.setProviderTradeNo(vo.getProviderTradeNo());
        row.setCreatedAt(format(vo.getCreatedAt()));
        row.setPaidAt(format(vo.getPaidAt()));
        row.setFulfilledAt(format(vo.getFulfilledAt()));
        row.setClosedAt(format(vo.getClosedAt()));
        return row;
    }

    private static BreakExportRow toBreakRow(BreakView b) {
        BreakExportRow row = new BreakExportRow();
        row.setId(b.id());
        row.setBreakType(b.breakType());
        row.setSeverity(b.severity());
        row.setStatus(b.status());
        row.setOrderNo(b.orderNo());
        row.setProviderTradeNo(b.providerTradeNo());
        row.setLocalAmountCents(b.localAmountCents());
        row.setProviderAmountCents(b.providerAmountCents());
        row.setDeltaCents(b.deltaCents());
        row.setResolutionCode(b.resolutionCode());
        row.setResolutionNote(b.resolutionNote());
        row.setCreatedAt(format(b.createdAt()));
        row.setResolvedAt(format(b.resolvedAt()));
        return row;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : TIME.format(time);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Data
    @ColumnWidth(22)
    public static class OrderExportRow {
        @ExcelProperty("订单号")
        private String orderNo;
        @ExcelProperty("用户名")
        private String username;
        @ExcelProperty("昵称")
        private String nickname;
        @ExcelProperty("商品")
        private String seriesTitle;
        @ExcelProperty("金额(分)")
        private Long priceCents;
        @ExcelProperty("币种")
        private String currency;
        @ExcelProperty("状态")
        private String status;
        @ExcelProperty("支付渠道")
        private String payChannel;
        @ExcelProperty("渠道交易号")
        private String providerTradeNo;
        @ExcelProperty("创建时间")
        private String createdAt;
        @ExcelProperty("支付时间")
        private String paidAt;
        @ExcelProperty("履约时间")
        private String fulfilledAt;
        @ExcelProperty("关闭时间")
        private String closedAt;
    }

    @Data
    @ColumnWidth(22)
    public static class BreakExportRow {
        @ExcelProperty("差异ID")
        private Long id;
        @ExcelProperty("差异类型")
        private String breakType;
        @ExcelProperty("严重度")
        private String severity;
        @ExcelProperty("状态")
        private String status;
        @ExcelProperty("订单号")
        private String orderNo;
        @ExcelProperty("渠道交易号")
        private String providerTradeNo;
        @ExcelProperty("本地金额(分)")
        private Long localAmountCents;
        @ExcelProperty("渠道金额(分)")
        private Long providerAmountCents;
        @ExcelProperty("差额(分)")
        private Long deltaCents;
        @ExcelProperty("处理码")
        private String resolutionCode;
        @ExcelProperty("处理备注")
        private String resolutionNote;
        @ExcelProperty("创建时间")
        private String createdAt;
        @ExcelProperty("处理时间")
        private String resolvedAt;
    }
}