package com.example.migrasi.service;

import com.example.migrasi.model.Province;
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
import java.util.Map;

@Slf4j
@Service
public class ProvinceMigration {

    private static final String FILE_PATH = "data/csv/provinsi.csv";
    private static final int SKIP_ROWS = 0;   // header
    private static final int BATCH_SIZE = 500;
    private static final int IDX_COL_0 = 0;
    private static final int IDX_COL_1 = 1;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager txManager;

    @Transactional
    public void migrate() {
        log.info("Running province migration from CSV: {}", FILE_PATH);

        List<Province> entities = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Path.of(FILE_PATH)), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = SKIP_ROWS;

            while ((line = br.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) continue;

                String[] cols = splitCsvSimple(line);

                String idStr = MyExcelDoc.getValueCsv(cols, IDX_COL_0);
                String name  = MyExcelDoc.getValueCsv(cols, IDX_COL_1);

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, idStr)) {
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

        BulkUpsertUtil.bulkUpsert(
                entities,
                Province.class,
                Integer.class,
                Province::getId,
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
