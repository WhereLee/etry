package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.commerce.api.AdminReconciliationOperations;
import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.aop.RateLimit;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reconciliation")
public class AdminReconciliationController {

    private final AdminReconciliationOperations operations;
    private final AdminExportService exportService;

    public AdminReconciliationController(AdminReconciliationOperations operations,
                                         AdminExportService exportService) {
        this.operations = operations;
        this.exportService = exportService;
    }

    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('recon:read')")
    @Measured("admin.recon.statements")
    public PageResult<AdminReconciliationOperations.StatementView> statements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operations.statements(page, size);
    }

    @PostMapping(value = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('recon:operate')")
    @RateLimit(key = "admin:reconUpload", limit = 10, windowSeconds = 60)
    @Measured("admin.recon.upload")
    @AdminAudit(action = "RECON_STATEMENT_UPLOAD", targetType = "RECONCILIATION")
    public AdminReconciliationOperations.StatementView upload(
            @RequestAttribute("userId") Long operatorId,
            @RequestParam(defaultValue = "ALIPAY") String provider,
            @RequestParam String statementDate,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择账单文件");
        }
        try {
            return operations.ingest(provider, LocalDate.parse(statementDate),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes(), operatorId);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("账单日期格式应为 yyyy-MM-dd");
        } catch (java.io.IOException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "读取账单文件失败");
        }
    }

    @GetMapping("/statements/{fileId}/runs")
    @PreAuthorize("hasAuthority('recon:read')")
    @Measured("admin.recon.runs")
    public List<AdminReconciliationOperations.RunView> runs(@PathVariable long fileId) {
        return operations.runs(fileId);
    }

    @PostMapping("/statements/{fileId}/runs")
    @PreAuthorize("hasAuthority('recon:operate')")
    @RateLimit(key = "admin:reconRun", limit = 10, windowSeconds = 60)
    @Measured("admin.recon.run")
    @AdminAudit(action = "RECON_RUN", targetType = "RECONCILIATION")
    public AdminReconciliationOperations.RunView run(
            @RequestAttribute("userId") Long operatorId,
            @PathVariable long fileId) {
        return operations.run(fileId, operatorId);
    }

    @GetMapping("/runs/{runId}/breaks")
    @PreAuthorize("hasAuthority('recon:read')")
    @Measured("admin.recon.breaks")
    public PageResult<AdminReconciliationOperations.BreakView> breaks(
            @PathVariable long runId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operations.breaks(runId, status, page, size);
    }

    @GetMapping("/runs/{runId}/breaks/export")
    @PreAuthorize("hasAuthority('recon:read')")
    @Measured("admin.recon.breaks.export")
    public void exportBreaks(@PathVariable long runId,
                             jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=recon-breaks-" + runId + ".xlsx");
        exportService.exportBreaks(response.getOutputStream(), runId);
    }

    @PostMapping("/breaks/{breakId}/actions")
    @PreAuthorize("hasAuthority('recon:operate')")
    @Measured("admin.recon.breakAction")
    @AdminAudit(action = "RECON_BREAK_ACTION", targetType = "RECONCILIATION")
    public AdminReconciliationOperations.BreakView act(
            @RequestAttribute("userId") Long operatorId,
            @PathVariable long breakId,
            @Valid @RequestBody BreakActionRequest request) {
        return operations.actOnBreak(breakId, request.action(), request.resolutionCode(),
                request.note(), operatorId);
    }

    public record BreakActionRequest(
            @NotBlank(message = "请选择处理动作") String action,
            String resolutionCode,
            @Size(max = 500, message = "处理说明过长") String note
    ) {
    }
}
