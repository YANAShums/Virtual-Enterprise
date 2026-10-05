package com.virtualenterprise.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XlsxReportWriter {
    private XlsxReportWriter() {}

    static byte[] write(String title, List<List<Cell>> rows) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                entry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                        </Types>""");
                entry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>""");
                entry(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>""".formatted(xml(title)));
                entry(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                        </Relationships>""");
                entry(zip, "xl/styles.xml", styles());
                entry(zip, "xl/worksheets/sheet1.xml", sheet(rows));
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать XLSX", exception);
        }
    }

    private static String sheet(List<List<Cell>> rows) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
                  <cols><col min="1" max="1" width="25" customWidth="1"/><col min="2" max="3" width="22" customWidth="1"/><col min="4" max="10" width="16" customWidth="1"/></cols><sheetData>
                """);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int excelRow = rowIndex + 1;
            xml.append("<row r=\"").append(excelRow).append("\">");
            List<Cell> cells = rows.get(rowIndex);
            for (int column = 0; column < cells.size(); column++) {
                Cell cell = cells.get(column);
                String reference = columnName(column) + excelRow;
                if (cell.numeric()) {
                    xml.append("<c r=\"").append(reference).append("\" t=\"n\" s=\"")
                            .append(cell.style()).append("\"><v>").append(xml(cell.value())).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(reference).append("\" t=\"inlineStr\" s=\"")
                            .append(cell.style()).append("\"><is><t xml:space=\"preserve\">")
                            .append(xml(cell.value())).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        if (rows.size() >= 5) xml.append("<autoFilter ref=\"A4:J").append(rows.size()).append("\"/>");
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="3"><font><sz val="11"/><name val="Onest"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Onest"/></font><font><b/><sz val="16"/><color rgb="FF063D37"/><name val="Onest"/></font></fonts>
                  <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF087F78"/><bgColor indexed="64"/></patternFill></fill></fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
                </styleSheet>""";
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String columnName(int index) {
        StringBuilder value = new StringBuilder();
        for (int number = index + 1; number > 0; number = (number - 1) / 26) {
            value.insert(0, (char) ('A' + (number - 1) % 26));
        }
        return value.toString();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    record Cell(String value, boolean numeric, int style) {
        static Cell text(String value) { return new Cell(value, false, 0); }
        static Cell number(Number value) { return new Cell(value == null ? "" : value.toString(), true, 0); }
        static Cell header(String value) { return new Cell(value, false, 1); }
        static Cell title(String value) { return new Cell(value, false, 2); }
    }
}
