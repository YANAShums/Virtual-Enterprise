package com.virtualenterprise.app.service;

import com.virtualenterprise.app.service.AttritionAnalyticsService.Filter;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Overview;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Row;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Печатное представление того же набора строк и фильтров, что используется для XLSX. */
final class PdfReportWriter {
    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float LEFT = 36;
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - LEFT * 2;
    private static final float[] COLUMN_WIDTHS = {145, 115, 115, 58, 68, 68, 96, 105};
    private static final String[] HEADERS = {
            "Сотрудник", "Подразделение", "Должность", "Загрузка", "Задачи", "Просрочено", "Вероятность", "Риск"
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private PdfReportWriter() {}

    static byte[] write(Overview report) {
        try (PDDocument document = new PDDocument();
             InputStream fontSource = PdfReportWriter.class.getResourceAsStream("/fonts/Onest.ttf");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontSource == null) throw new IllegalStateException("PDF font resource is missing");
            PDType0Font font = PDType0Font.load(document, fontSource);
            LocalDateTime generatedAt = LocalDateTime.now();
            int pageNumber = 1;
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float rowTop = drawHeader(content, font, report, generatedAt, true);
            try {
                if (report.rows().isEmpty()) {
                    drawText(content, font, 10, "Нет данных для выбранных фильтров.", LEFT + 5, rowTop - 18, CONTENT_WIDTH - 10);
                }
                for (Row row : report.rows()) {
                    if (rowTop - 38 < 52) {
                        drawFooter(content, font, pageNumber++);
                        content.close();
                        page = new PDPage(PAGE_SIZE);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        rowTop = drawHeader(content, font, report, generatedAt, false);
                    }
                    drawRow(content, font, row, rowTop);
                    rowTop -= 38;
                }
                drawFooter(content, font, pageNumber);
            } finally {
                content.close();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Не удалось сформировать PDF-отчёт", exception);
        }
    }

    private static float drawHeader(PDPageContentStream content, PDType0Font font, Overview report,
                                    LocalDateTime generatedAt, boolean firstPage) throws IOException {
        drawText(content, font, 17, "Управленческий отчёт FlowStaff AI", LEFT, 551, CONTENT_WIDTH);
        if (firstPage) {
            Filter filter = report.filter();
            drawText(content, font, 9, "Сформирован: " + generatedAt.format(DATE_TIME), LEFT, 529, CONTENT_WIDTH);
            drawText(content, font, 9, "Период: " + date(filter.from()) + " - " + date(filter.to()), LEFT, 514, CONTENT_WIDTH);
            drawText(content, font, 9, "Подразделение: " + identifier(filter.departmentId())
                    + "   Должность: " + identifier(filter.positionId())
                    + "   Проект: " + identifier(filter.projectId()), LEFT, 499, CONTENT_WIDTH);
            drawText(content, font, 9, "Уровень риска: " + text(filter.riskLevel(), "все")
                    + "   Вероятность от: " + number(filter.minProbability())
                    + "   до: " + number(filter.maxProbability()), LEFT, 484, CONTENT_WIDTH);
            drawText(content, font, 9, "Сотрудников: " + report.rows().size()
                    + "   Высокий риск: " + report.highCount()
                    + "   Критический риск: " + report.criticalCount(), LEFT, 462, CONTENT_WIDTH);
            return drawTableHeader(content, font, 445);
        }
        drawText(content, font, 9, "Продолжение. Сформирован: " + generatedAt.format(DATE_TIME), LEFT, 529, CONTENT_WIDTH);
        return drawTableHeader(content, font, 507);
    }

    private static float drawTableHeader(PDPageContentStream content, PDType0Font font, float top) throws IOException {
        content.setNonStrokingColor(232 / 255f, 232 / 255f, 232 / 255f);
        content.addRect(LEFT, top - 26, CONTENT_WIDTH, 26);
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
        content.setLineWidth(0.7f);
        content.addRect(LEFT, top - 26, CONTENT_WIDTH, 26);
        content.stroke();
        float x = LEFT;
        for (int i = 0; i < HEADERS.length; i++) {
            drawText(content, font, 8, HEADERS[i], x + 4, top - 17, COLUMN_WIDTHS[i] - 8);
            x += COLUMN_WIDTHS[i];
        }
        return top - 26;
    }

    private static void drawRow(PDPageContentStream content, PDType0Font font, Row row, float top) throws IOException {
        String[] values = {
                text(row.employeeName(), "-"), text(row.departmentName(), "-"), text(row.positionName(), "-"),
                row.workloadPercent() == null ? "-" : number(row.workloadPercent()) + " %",
                Integer.toString(row.activeTasks()), Integer.toString(row.overdueTasks()),
                number(row.probabilityPercent()) + " %", text(row.riskLevel(), "-")
        };
        float x = LEFT;
        for (int i = 0; i < values.length; i++) {
            drawText(content, font, 8.5f, values[i], x + 4, top - 15, COLUMN_WIDTHS[i] - 8);
            x += COLUMN_WIDTHS[i];
        }
        String calculated = row.calculatedAt() == null ? "-" : row.calculatedAt().format(DATE_TIME);
        drawText(content, font, 7.5f, "Расчёт: " + calculated + "   Модель: " + text(row.modelVersion(), "-"),
                LEFT + 4, top - 29, CONTENT_WIDTH - 8);
        content.setLineWidth(0.35f);
        content.moveTo(LEFT, top - 38);
        content.lineTo(LEFT + CONTENT_WIDTH, top - 38);
        content.stroke();
    }

    private static void drawFooter(PDPageContentStream content, PDType0Font font, int pageNumber) throws IOException {
        drawText(content, font, 8, "FlowStaff AI", LEFT, 25, 200);
        drawText(content, font, 8, "Страница " + pageNumber, LEFT + CONTENT_WIDTH - 100, 25, 100);
    }

    private static void drawText(PDPageContentStream content, PDType0Font font, float size,
                                 String value, float x, float y, float maxWidth) throws IOException {
        String fitted = fit(font, size, value, maxWidth);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(fitted);
        content.endText();
    }

    private static String fit(PDType0Font font, float size, String value, float maxWidth) throws IOException {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ");
        if (font.getStringWidth(clean) * size / 1000 <= maxWidth) return clean;
        String suffix = "...";
        int end = clean.length();
        while (end > 0 && font.getStringWidth(clean.substring(0, end) + suffix) * size / 1000 > maxWidth) end--;
        return clean.substring(0, end) + suffix;
    }

    private static String date(LocalDate value) { return value == null ? "не задана" : value.format(DATE); }
    private static String identifier(Long value) { return value == null ? "все" : "ID " + value; }
    private static String number(BigDecimal value) { return value == null ? "не задана" : value.stripTrailingZeros().toPlainString(); }
    private static String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
