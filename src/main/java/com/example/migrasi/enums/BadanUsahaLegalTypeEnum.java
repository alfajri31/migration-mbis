package com.example.migrasi.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum BadanUsahaLegalTypeEnum {

    PERORANGAN("Perorangan", "perorangan"),
    PT("Perseroan Terbatas", "pt"),
    PT_PERORANGAN("PT Perorangan", "pt"),
    PT_TBK("Perseroan Terbatas Terbuka", "pt tbk"),
    CV("Commanditaire Vennootschap", "cv"),
    FIRMA("Firma", "firma", "fa"),
    UD("Usaha Dagang", "ud"),
    KOPERASI("Koperasi", "koperasi"),
    BUMN("Badan Usaha Milik Negara", "bumn"),
    BUMD("Badan Usaha Milik Daerah", "bumd"),
    PMA("Penanaman Modal Asing", "pma"),
    LAINNYA("Lainnya");

    private final String value;
    private final List<String> prefixes;

    BadanUsahaLegalTypeEnum(String value, String... prefixes) {
        this.value = value;
        this.prefixes = Arrays.asList(prefixes);
    }
}