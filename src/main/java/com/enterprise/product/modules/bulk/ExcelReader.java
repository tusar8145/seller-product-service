package com.enterprise.product.modules.bulk;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
public class ExcelReader {

    private static final int BATCH_SIZE = 1000;

    public void streamRows(InputStream inputStream,
                           ExcelSchema schema,
                           Consumer<List<ExcelRow>> batchConsumer) {
        try (OPCPackage pkg = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    SheetRowHandler handler = new SheetRowHandler(schema, batchConsumer);
                    XSSFSheetXMLHandler xmlHandler = new XSSFSheetXMLHandler(
                            styles, null, sst, handler, new DataFormatter(), false);
                    var parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(xmlHandler);
                    parser.parse(new InputSource(sheetStream));
                    handler.flush();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel parsing failed", e);
        }
    }

    // ────────────────────────────────────────────────────────────

    private static class SheetRowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final ExcelSchema schema;
        private final Consumer<List<ExcelRow>> consumer;
        private final List<ExcelRow> buffer = new ArrayList<>(BATCH_SIZE);
        private ExcelRow current;

        SheetRowHandler(ExcelSchema schema, Consumer<List<ExcelRow>> consumer) {
            this.schema = schema;
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            current = rowNum > 0 ? new ExcelRow() : null;   // row 0 = header, skip
        }

        @Override
        public void endRow(int rowNum) {
            if (current != null) {
                current.setRowNumber(rowNum + 1);
                buffer.add(current);
                if (buffer.size() >= BATCH_SIZE) {
                    consumer.accept(new ArrayList<>(buffer));
                    buffer.clear();
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (current == null) return;

            int col = columnIndex(cellReference);   // 0-based, from "A1"/"B2" etc.
            String v = trim(formattedValue);

            switch (schema) {
                case PRODUCT_CREATE -> {
                    switch (col) {
                        case 0 -> current.setName(v);
                        case 1 -> current.setDetails(v);
                        case 2 -> current.setImage(v);
                        case 3 -> current.setStock(parseLong(v));
                        case 4 -> current.setPrice(parseDecimal(v));
                        default -> { /* ignore */ }
                    }
                }
                case STOCK_UPDATE -> {
                    switch (col) {
                        case 0 -> current.setId(parseLong(v));      // ← Id
                        case 1 -> current.setStock(parseLong(v));   // ← Stock (signed delta)
                        default -> { /* ignore */ }
                    }
                }
            }
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {}

        void flush() {
            if (!buffer.isEmpty()) {
                consumer.accept(new ArrayList<>(buffer));
                buffer.clear();
            }
        }

        // ── helpers ─────────────────────────────────────────────

        private static int columnIndex(String ref) {
            if (ref == null || ref.isEmpty()) return -1;
            int col = 0;
            for (int i = 0; i < ref.length(); i++) {
                char c = ref.charAt(i);
                if (c >= 'A' && c <= 'Z') col = col * 26 + (c - 'A' + 1);
                else break;
            }
            return col - 1;
        }

        private static String trim(String s) { return s == null ? null : s.trim(); }

        private static Long parseLong(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                String t = s.replace(",", "");
                if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
                return Long.valueOf(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static BigDecimal parseDecimal(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return new BigDecimal(s.replace(",", "").replace("$", "").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}