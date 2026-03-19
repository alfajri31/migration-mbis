package com.example.migrasi.util;

import lombok.experimental.UtilityClass;

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

}
