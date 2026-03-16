package com.example.migrasi.service;

import com.example.migrasi.model.District;
import com.example.migrasi.util.MyExcelDoc;
import com.example.migrasi.util.RowSkipUtil;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DistrictMigration {

    private static final String FILE_PATH = "data/csv/kecamatan.csv";
    private static final int SKIP_ROWS = 0;   // header
    private static final int BATCH_SIZE = 500;
    private static final int IDX_ID = 0;
    private static final int IDX_kecamatan_name = 1;
    private static final int IDX_kota_id = 2;


    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void migrate() {
        log.info("Running province migration from CSV: {}", FILE_PATH);

        List<District> entities = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Path.of(FILE_PATH)), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = SKIP_ROWS;

            while ((line = br.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) continue;

                String[] cols = splitCsvSimple(line);

                String idStr = MyExcelDoc.getValueCsv(cols, IDX_ID);
                String idKota  = MyExcelDoc.getValueCsv(cols, IDX_kota_id);
                String name  = MyExcelDoc.getValueCsv(cols, IDX_kecamatan_name);

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, idStr)) {
                    continue;
                }


                District p = new District();
                p.setId(Integer.parseInt(idStr));
                p.setIdKota(Integer.parseInt(idKota));
                p.setNama(name);
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
    public void bulkUpsert(List<District> entities) {
        try {
            if (entities == null || entities.isEmpty()) return;

            int processed = 0;

            for (District e : entities) {
                if (e.getId() == null) {
                    log.warn("Skip: cif kosong");
                    continue;
                }

                Integer existingId = entityManager.createQuery(
                                "select c.id from District c where c.id = :id", Integer.class)
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

    // Split sederhana: cukup untuk CSV yang tidak punya koma di dalam quote.
    // Kalau CSV kamu kompleks (ada koma dalam quotes), bilang ya—aku kasih parser yang handle quotes.
    private String[] splitCsvSimple(String line) {
        return line.split(",", -1);
    }
}
