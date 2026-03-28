package com.example.migrasi.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum CustomerLegalTypeEnum {

    PERORANGAN("Perorangan"),
    PT("PT"),
    CV("CV"),
    TBK("TBK"),
    LAINNYA("Lainnya");

    private final String value;

    CustomerLegalTypeEnum(String value) {
        this.value = value;
    }

    public static CustomerLegalTypeEnum from(String id) {

        if (id == null) throw new IllegalArgumentException("Entity Type wajib diisi");

        return Arrays.stream(values())
                .filter(e -> e.name().equals(id.trim()))
                .findFirst().orElse(LAINNYA);
    }
}
