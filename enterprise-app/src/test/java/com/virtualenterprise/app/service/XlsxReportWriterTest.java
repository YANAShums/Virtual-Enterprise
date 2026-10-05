package com.virtualenterprise.app.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxReportWriterTest {
    @Test
    void writesValidOfficeArchiveWithEscapedWorksheetData() throws Exception {
        byte[] bytes = XlsxReportWriter.write("Аналитика", List.of(
                List.of(XlsxReportWriter.Cell.title("Отчёт")),
                List.of(XlsxReportWriter.Cell.text("Период")),
                List.of(XlsxReportWriter.Cell.text("Фильтр")),
                List.of(XlsxReportWriter.Cell.header("Сотрудник")),
                List.of(XlsxReportWriter.Cell.text("Иванов & <Иван>"))));

        List<String> entries = new ArrayList<>();
        String worksheet = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    worksheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertThat(entries).contains("[Content_Types].xml", "xl/workbook.xml",
                "xl/styles.xml", "xl/worksheets/sheet1.xml");
        assertThat(worksheet).contains("Иванов &amp; &lt;Иван&gt;").contains("autoFilter");
    }
}
