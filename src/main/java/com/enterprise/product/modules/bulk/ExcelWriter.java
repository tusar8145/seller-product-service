package com.enterprise.product.modules.bulk;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class ExcelWriter {

    public byte[] writeFailures(List<ExcelFailure> failures) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            SXSSFSheet sheet = wb.createSheet("Failures");
            String[] cols = {"Row", "Identifier", "Error"};

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (ExcelFailure f : failures) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(f.getRowNumber());
                row.createCell(1).setCellValue(safe(f.getIdentifier()));
                row.createCell(2).setCellValue(safe(f.getErrorMessage()));
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write failure report", e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
