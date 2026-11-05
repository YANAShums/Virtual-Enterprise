package com.virtualenterprise.app.service;

import com.virtualenterprise.app.service.AttritionAnalyticsService.Filter;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Overview;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ManagementReportService {
    private final AttritionAnalyticsService analytics;

    public ManagementReportService(AttritionAnalyticsService analytics) { this.analytics = analytics; }

    public Overview report(Filter filter) { return analytics.overview(filter); }

    public byte[] pdf(Overview report) { return PdfReportWriter.write(report); }

    public byte[] xlsx(Overview report) {
        List<List<XlsxReportWriter.Cell>> rows = new ArrayList<>();
        rows.add(List.of(XlsxReportWriter.Cell.title("Управленческий отчёт FlowStaff AI")));
        rows.add(List.of(XlsxReportWriter.Cell.text("Сформирован: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))));
        rows.add(List.of(XlsxReportWriter.Cell.text("Период: "
                + report.filter().from().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " — "
                + report.filter().to().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))));
        rows.add(List.of("Сотрудник", "Подразделение", "Должность", "Загрузка, %", "Активные задачи",
                "Просрочено", "Вероятность, %", "Риск", "Дата расчёта", "Модель").stream()
                .map(XlsxReportWriter.Cell::header).toList());
        for (Row row : report.rows()) {
            rows.add(List.of(XlsxReportWriter.Cell.text(row.employeeName()),
                    XlsxReportWriter.Cell.text(row.departmentName()), XlsxReportWriter.Cell.text(row.positionName()),
                    row.workloadPercent() == null ? XlsxReportWriter.Cell.text("") : XlsxReportWriter.Cell.number(row.workloadPercent()),
                    XlsxReportWriter.Cell.number(row.activeTasks()), XlsxReportWriter.Cell.number(row.overdueTasks()),
                    XlsxReportWriter.Cell.number(row.probabilityPercent()), XlsxReportWriter.Cell.text(row.riskLevel()),
                    XlsxReportWriter.Cell.text(row.calculatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))),
                    XlsxReportWriter.Cell.text(row.modelVersion())));
        }
        return XlsxReportWriter.write("Аналитика", rows);
    }

}
