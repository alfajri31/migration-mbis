package com.example.migrasi.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@UtilityClass
public class MyExcelDoc {

    private static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");

    public static String getValueExcel(Row row, Map<String, Integer> colIndex, String columnName) {
        Integer idx = colIndex.get(columnName.trim().toUpperCase());
        if (idx == null) return null;

        Cell cell = row.getCell(idx);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> Normalizing.removeSpace(cell.getStringCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                long lv = (long) v;
                yield (v == lv) ? String.valueOf(lv) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> Normalizing.removeSpace(cell.getStringCellValue());
                case NUMERIC -> {
                    double v = cell.getNumericCellValue();
                    long lv = (long) v;
                    yield (v == lv) ? String.valueOf(lv) : String.valueOf(v);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> null;
            };
            case BLANK, _NONE, ERROR -> null;
        };
    }

    public static Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            if (cell == null) continue;

            String name = cell.getStringCellValue();
            if (name == null) continue;

            String key = name.trim().toUpperCase();
            if (!key.isEmpty()) {
                map.put(key, cell.getColumnIndex());
            }
        }
        log.info("Detected columns: {}", map.keySet());
        return map;
    }

    public static String getValueCsv(String[] cols, int idx) {
        if (cols == null) return null;
        if (idx < 0 || idx >= cols.length) return null;
        String v = cols[idx];
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;

        // 1. Kalau sudah OffsetDateTime → convert ke Jakarta
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value)
                    .atZoneSameInstant(JAKARTA_ZONE)
                    .toOffsetDateTime();
        }

        // 2. Kalau java.util.Date
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant()
                    .atZone(JAKARTA_ZONE)
                    .toOffsetDateTime();
        }

        // 3. Kalau String
        if (value instanceof String) {
            String str = ((String) value).trim();

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            return LocalDateTime.parse(str, formatter)
                    .atZone(JAKARTA_ZONE)
                    .toOffsetDateTime();
        }

        throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }

    public static List<Map<String, String>> findAllByColumn(
            MultipartFile file,
            int sheetIndex,
            String columnName,
            String value
    ) {

        List<Map<String, String>> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();

             Workbook workbook = new XSSFWorkbook(is)) {

            if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalArgumentException(
                        "Sheet index tidak valid: " + sheetIndex +
                                ", total sheet: " + workbook.getNumberOfSheets()
                );
            }

            Sheet sheet = workbook.getSheetAt(sheetIndex);

            Iterator<Row> rows = sheet.iterator();

            if (!rows.hasNext()) return result;

            Row headerRow = rows.next();
            Map<String, Integer> colIndex = buildColumnIndex(headerRow);

            while (rows.hasNext()) {
                Row row = rows.next();

                String cellValue = getValueExcel(row, colIndex, columnName);

                if (cellValue != null && cellValue.equalsIgnoreCase(value)) {

                    Map<String, String> data = new HashMap<>();

                    for (String col : colIndex.keySet()) {
                        data.put(col, getValueExcel(row, colIndex, col));
                    }

                    result.add(data);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Gagal baca Excel", e);
        }

        return result;
    }

    public static Integer parseIntOrDefault(String value, int defaultValue) {
        try {
            return (value == null || value.isBlank())
                    ? defaultValue
                    : Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Long parseLongOrDefault(String value, long defaultValue) {
        try {
            return (value == null || value.isBlank())
                    ? defaultValue
                    : Long.parseLong(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }



}
