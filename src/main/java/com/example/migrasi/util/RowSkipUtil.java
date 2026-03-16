package com.example.migrasi.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowSkipUtil {

    // untuk CSV (biasanya hanya 1 ID field)
    public static boolean skipIdField(int rowNumber,String value) {
        if (value == null || value.isBlank()) {
            log.warn("Skip row {}: {} kosong", rowNumber);
            return true;
        }
        return false;
    }
}