package com.example.migrasi.util;

import lombok.experimental.UtilityClass;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
public class OCRUtil {

    private static final ITesseract tesseract = new Tesseract();

    static {
        URL resource = OCRUtil.class.getClassLoader().getResource("assets");
        if (resource == null) {
            throw new RuntimeException("Folder assets tidak ditemukan!");
        }
        tesseract.setDatapath(new File(resource.getPath()).getAbsolutePath());
        tesseract.setLanguage("ind+eng");
    }

    public static Map<String, String> extractFromResources(String path) {
        Map<String, String> result = new LinkedHashMap<>();

        try {
            URL resource = OCRUtil.class.getClassLoader().getResource(path);
            if (resource == null) {
                throw new RuntimeException("Folder images tidak ditemukan!");
            }

            File folder = new File(resource.getPath());
            File[] files = folder.listFiles();

            if (files == null) return result;

            Arrays.sort(files);

            for (File file : files) {
                if (isImage(file)) {
                    String text = tesseract.doOCR(file);
                    result.put(file.getName(), text);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("OCR gagal: " + e.getMessage());
        }

        return result;
    }

    private static boolean isImage(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}