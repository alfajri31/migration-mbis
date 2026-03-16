package com.example.migrasi.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MyExcelDoc {

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



}
