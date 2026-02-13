package com.example.migrasi.service;

import com.example.migrasi.model.Province;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class ProvinceMigration {

    private static final String FILE_PATH = "data/csv/provinsi.csv";
    private static final int SKIP_ROWS = 1;   // header
    private static final int BATCH_SIZE = 500;
    private static final int IDX_COL_0 = 0;
    private static final int IDX_COL_1 = 1;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void migrate() {
        log.info("Running province migration from CSV: {}", FILE_PATH);

        List<Province> entities = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Path.of(FILE_PATH)), StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null) throw new IllegalStateException("CSV kosong: " + FILE_PATH);

            Map<String, Integer> colIndex = buildColumnIndexCsv(headerLine);

            // skip tambahan kalau SKIP_ROWS > 1
            for (int i = 1; i < SKIP_ROWS; i++) {
                br.readLine();
            }

            String line;
            int rowNumber = SKIP_ROWS;

            while ((line = br.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) continue;

                String[] cols = splitCsvSimple(line);

                String idStr = getByIndex(cols, IDX_COL_0);
                String name  = getByIndex(cols, IDX_COL_1);

                if (idStr == null || idStr.isBlank()) {
                    log.warn("Skip row {}: id kosong", rowNumber);
                    continue;
                }

                Province p = new Province();
                p.setId(Integer.parseInt(idStr));
                p.setNama(name.trim());
                entities.add(p);
            }

            log.info("Total rows read (valid): {}", entities.size());

        } catch (Exception ex) {
            log.error("Gagal membaca CSV: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca CSV: " + FILE_PATH, ex);
        }

        bulkUpsert(entities);

        log.info("Province migration selesai.");
    }

    @jakarta.transaction.Transactional
    public void bulkUpsert(List<Province> entities) {
        try {
            if (entities == null || entities.isEmpty()) return;

            int processed = 0;

            for (Province e : entities) {
                if (e.getId() == null) {
                    log.warn("Skip: cif kosong");
                    continue;
                }

                Integer existingId = entityManager.createQuery(
                                "select c.id from Province c where c.id = :id", Integer.class)
                        .setParameter("id", e.getId())
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst().orElse(null);

                if (existingId != null) {
                    e.setId(existingId);     // penting: set PK supaya merge = UPDATE
                    entityManager.merge(e);
                } else {
                    entityManager.persist(e); // INSERT (id akan di-generate kalau mapping benar)
                }

                if (++processed % BATCH_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    log.info("Processed: {}", processed);
                }
            }

            entityManager.flush();
            entityManager.clear();
            log.info("Total processed: {}", processed);
        }catch (Exception e) {
            log.info("something error occured when upsert: ",e);
            throw  e;
        }
    }

    private Map<String, Integer> buildColumnIndexCsv(String headerLine) {
        String[] headers = splitCsvSimple(headerLine);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = normalizeHeader(headers[i]);
            if (!key.isEmpty()) map.put(key, i);
        }
        log.info("Detected CSV columns: {}", map.keySet());
        return map;
    }

    private String normalizeHeader(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').trim().toUpperCase();
    }

    private String getValueCsv(String[] cols, Map<String, Integer> colIndex, String columnName) {
        Integer idx = colIndex.get(columnName.trim().toUpperCase());
        if (idx == null) return null;
        if (idx < 0 || idx >= cols.length) return null;

        String v = cols[idx];
        if (v == null) return null;

        // buang quote kalau ada
        v = v.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }

        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    // Split sederhana: cukup untuk CSV yang tidak punya koma di dalam quote.
    // Kalau CSV kamu kompleks (ada koma dalam quotes), bilang ya—aku kasih parser yang handle quotes.
    private String[] splitCsvSimple(String line) {
        return line.split(",", -1);
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getByIndex(String[] cols, int idx) {
        if (cols == null) return null;
        if (idx < 0 || idx >= cols.length) return null;
        String v = cols[idx];
        return (v == null || v.isBlank()) ? null : v.trim();
    }

}
