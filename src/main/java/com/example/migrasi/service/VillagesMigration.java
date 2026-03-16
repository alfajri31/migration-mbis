package com.example.migrasi.service;

import com.example.migrasi.model.Village;
import com.example.migrasi.util.BulkUpsertUtil;
import com.example.migrasi.util.MyExcelDoc;
import com.example.migrasi.util.RowSkipUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VillagesMigration {

    private static final String FILE_PATH = "data/csv/kelurahan.csv";
    private static final int SKIP_ROWS = 0;   // header
    private static final int BATCH_SIZE = 500;
    private static final int IDX_ID = 0;
    private static final int IDX_kelurahan_name = 1;
    private static final int IDX_kecamatan_id = 2;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager txManager;

    @Transactional
    public void migrate() {
        log.info("Running province migration from CSV: {}", FILE_PATH);

        List<Village> entities = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Path.of(FILE_PATH)), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = SKIP_ROWS;

            while ((line = br.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) continue;

                String[] cols = splitCsvSimple(line);

                String idStr = MyExcelDoc.getValueCsv(cols, IDX_ID);
                String idKecamatan  = MyExcelDoc.getValueCsv(cols, IDX_kecamatan_id);
                String name  = MyExcelDoc.getValueCsv(cols, IDX_kelurahan_name);

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, idStr)) {
                    continue;
                }

                Village p = new Village();
                p.setId(Long.parseLong(idStr));
                p.setIdKecamatan(Integer.parseInt(idKecamatan));
                p.setNama(name);
                entities.add(p);
            }

            log.info("Total rows read (valid): {}", entities.size());

        } catch (Exception ex) {
            log.error("Gagal membaca CSV: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca CSV: " + FILE_PATH, ex);
        }

        BulkUpsertUtil.bulkUpsert(
                entities,
                Village.class,
                Long.class,
                Village::getId,
                "id",
                entityManager,
                txManager
        );

        log.info("Province migration selesai.");
    }

    // Split sederhana: cukup untuk CSV yang tidak punya koma di dalam quote.
    // Kalau CSV kamu kompleks (ada koma dalam quotes), bilang ya—aku kasih parser yang handle quotes.
    private String[] splitCsvSimple(String line) {
        return line.split(",", -1);
    }


}
