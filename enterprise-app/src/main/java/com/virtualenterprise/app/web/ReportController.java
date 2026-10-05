package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.AttritionAnalyticsService;
import com.virtualenterprise.app.service.ManagementReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
public class ReportController {
    private final ManagementReportService reports;

    public ReportController(ManagementReportService reports) { this.reports = reports; }

    @GetMapping("/reports")
    String index(@RequestParam(required = false) LocalDate from,
                 @RequestParam(required = false) LocalDate to,
                 @RequestParam(required = false) Long departmentId,
                 @RequestParam(required = false) Long positionId,
                 @RequestParam(required = false) Long projectId,
                 @RequestParam(required = false) String riskLevel,
                 @RequestParam(required = false) BigDecimal minProbability,
                 @RequestParam(required = false) BigDecimal maxProbability,
                 Model model) {
        var report = reports.report(filter(from, to, departmentId, positionId, projectId,
                riskLevel, minProbability, maxProbability));
        model.addAttribute("report", report);
        model.addAttribute("activeTaskTotal", report.rows().stream().mapToLong(AttritionAnalyticsService.Row::activeTasks).sum());
        model.addAttribute("overdueTaskTotal", report.rows().stream().mapToLong(AttritionAnalyticsService.Row::overdueTasks).sum());
        model.addAttribute("generatedAt", LocalDateTime.now());
        return "reports/index";
    }

    @GetMapping("/reports/export")
    ResponseEntity<byte[]> export(@RequestParam String format,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to,
                                  @RequestParam(required = false) Long departmentId,
                                  @RequestParam(required = false) Long positionId,
                                  @RequestParam(required = false) Long projectId,
                                  @RequestParam(required = false) String riskLevel,
                                  @RequestParam(required = false) BigDecimal minProbability,
                                  @RequestParam(required = false) BigDecimal maxProbability) {
        var report = reports.report(filter(from, to, departmentId, positionId, projectId,
                riskLevel, minProbability, maxProbability));
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        if (!xlsx && !"csv".equalsIgnoreCase(format)) {
            throw new com.virtualenterprise.app.service.AppException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_REPORT_FORMAT", "Поддерживаются форматы CSV и XLSX");
        }
        byte[] body = xlsx ? reports.xlsx(report) : reports.csv(report);
        String extension = xlsx ? "xlsx" : "csv";
        MediaType type = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv;charset=UTF-8");
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=flowstaff-management-" + LocalDate.now() + "." + extension)
                .body(body);
    }

    private AttritionAnalyticsService.Filter filter(LocalDate from, LocalDate to, Long departmentId,
                                                     Long positionId, Long projectId, String riskLevel,
                                                     BigDecimal minProbability, BigDecimal maxProbability) {
        return new AttritionAnalyticsService.Filter(from, to, departmentId, positionId, projectId,
                riskLevel, minProbability, maxProbability);
    }
}
