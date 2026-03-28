package com.example.migrasi.util;

import com.example.migrasi.enums.BadanUsahaLegalTypeEnum;
import com.example.migrasi.enums.CustomerLegalTypeEnum;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class Normalizing {

    public static String normalizeNpwp(String npwp) {
        if (npwp == null || npwp.isEmpty()) {
            return npwp;
        }

        npwp = npwp.trim();

        // hapus semua karakter selain angka
        npwp = npwp.replaceAll("[^0-9]", "");

        return npwp;
    }

    public static String normalizePhone(String hp) {
        if (hp == null || hp.isEmpty()) {
            return hp;
        }

        hp = hp.trim();

        // hilangkan tanda "-"
        hp = hp.replace("-", "");

        if (hp.startsWith("08")) {
            hp = "62" + hp.substring(1);
        } else {
            hp = "";
        }

        return hp;
    }

    public static String removeSpace(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public String normalizeCompanyName(String name) {
        if (name == null) return null;

        String lower = name.toLowerCase();

        String foundPrefix = null;

        for (BadanUsahaLegalTypeEnum type : BadanUsahaLegalTypeEnum.values()) {
            
            for (String prefix : type.getPrefixes()) {

                Pattern pattern = Pattern.compile("\\b" + prefix + "\\b");

                if (pattern.matcher(lower).find()) {
                    foundPrefix = type.getPrefixes().get(0); // ambil prefix utama
                    lower = lower.replaceAll("\\b" + prefix + "\\b", " ");
                }
            }
        }

        // bersihkan semua selain huruf & angka
        String cleaned = lower.replaceAll("[^a-z0-9]", "");

        // taruh prefix di depan
        if (foundPrefix != null) {
            return foundPrefix.replaceAll("\\s+", "") + cleaned;
        }

        return cleaned;
    }

    public String normalizingMbisLegalType(String jenisPerusahaan) {
        if(jenisPerusahaan!=null && !jenisPerusahaan.isEmpty()) {
            if(CustomerLegalTypeEnum.from(jenisPerusahaan).name().equals(jenisPerusahaan)) {
                return jenisPerusahaan.toUpperCase();
            }
        }
        return null;
    }

}
