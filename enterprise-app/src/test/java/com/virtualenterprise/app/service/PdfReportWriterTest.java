package com.virtualenterprise.app.service;

import com.virtualenterprise.app.service.AttritionAnalyticsService.Filter;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Overview;
import com.virtualenterprise.app.service.AttritionAnalyticsService.Row;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportWriterTest {
    @Test
    void write_withEmployee_containsReadableTextAndFilters() throws Exception {
        byte[] pdf = PdfReportWriter.write(overview(List.of(row(1))));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Иванова Анна", "Отдел разработки", "Подразделение: ID 2",
                    "Уровень риска: HIGH", "Модель: v1");
            var image = new PDFRenderer(document).renderImageWithDPI(0, 120);
            assertThat(image.getWidth()).isGreaterThan(1000);
            if (Boolean.getBoolean("flowstaff.pdf.preview")) {
                Path review = Path.of("target", "pdf-review");
                Files.createDirectories(review);
                Files.write(review.resolve("management-report.pdf"), pdf);
                ImageIO.write(image, "png", review.resolve("management-report.png").toFile());
            }
        }
    }

    @Test
    void write_withManyEmployees_addsPages() throws Exception {
        byte[] pdf = PdfReportWriter.write(overview(IntStream.rangeClosed(1, 25).mapToObj(this::row).toList()));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Сотрудник 25");
        }
    }

    @Test
    void write_withoutEmployees_showsEmptyState() throws Exception {
        byte[] pdf = PdfReportWriter.write(overview(List.of()));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Нет данных для выбранных фильтров");
        }
    }

    private Overview overview(List<Row> rows) {
        Filter filter = new Filter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                2L, null, null, "HIGH", new BigDecimal("0.60"), null);
        return new Overview(filter, rows, rows.size(), 0, new BigDecimal("0.72"), null,
                Map.of(), "", List.of(), "", List.of(), List.of(), List.of());
    }

    private Row row(int number) {
        String name = number == 1 ? "Иванова Анна" : "Сотрудник " + number;
        return new Row((long) number, name, 2L, "Отдел разработки", 3L, "Инженер",
                LocalDate.of(2026, 3, 31), LocalDateTime.of(2026, 3, 31, 10, 15),
                new BigDecimal("0.72"), new BigDecimal("72.0"), "HIGH", "v1", List.of(),
                new BigDecimal("87.5"), "NORMAL", 4, 1, false);
    }
}
