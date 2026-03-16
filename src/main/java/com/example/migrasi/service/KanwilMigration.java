package com.example.migrasi.service;

import com.example.migrasi.model.Branch;
import com.example.migrasi.util.RowSkipUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.example.migrasi.util.MyExcelDoc.*;

@Slf4j
@Service
public class KanwilMigration {

    private static final String FILE_PATH = "data/xlsx/customer.xlsx";
    private static final String SHEET_NAME = "KC"; // nama tab excel
    private static final int SKIP_ROWS = 1;                 // header row
    private static final int BATCH_SIZE = 500;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void migrate() {
        log.info("Running migration from sheet: {}", SHEET_NAME);

        List<Branch> entities = new ArrayList<>();

        try (InputStream is = Files.newInputStream(Path.of(FILE_PATH));
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("Sheet tidak ditemukan: " + SHEET_NAME);
            }

            Iterator<Row> rows = sheet.iterator();
            if (!rows.hasNext()) {
                throw new IllegalStateException("Sheet kosong: " + SHEET_NAME);
            }

            // header
            Row headerRow = rows.next();
            Map<String, Integer> colIndex = buildColumnIndex(headerRow);

            int rowNumber = 1;

            // skip tambahan kalau SKIP_ROWS > 1
            while (rowNumber < SKIP_ROWS && rows.hasNext()) {
                rows.next();
                rowNumber++;
            }

            while (rows.hasNext()) {
                Row row = rows.next();
                rowNumber++;

                // Ambil berdasarkan NAMA KOLOM Excel
                String kantorCabang = getValueExcel(row, colIndex, "Kantor Cabang");
                String kantorWil = getValueExcel(row, colIndex, "Kanwil");

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, kantorCabang)) {
                    continue;
                }


                String codeCabang = kantorCabang.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");

                /*
                 ************************************************************************************************************************
                 */
                Branch e = new Branch();
                e.setRegion(kantorWil);
                e.setCode(codeCabang);
                e.setName(kantorCabang);
                entities.add(e);
            }

            log.info("Total rows read (valid): {}", entities.size());

        } catch (Exception ex) {
            log.error("Gagal membaca Excel: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca Excel: " + FILE_PATH, ex);
        }

        bulkUpsert(entities);

        log.info("Migration selesai.");
    }

    public void bulkUpsert(List<Branch> entities) {
        if (entities == null || entities.isEmpty()) return;

        int processed = 0;

        for (Branch e : entities) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            TransactionStatus status = txManager.getTransaction(def); // BEGIN

            try {
                if (e.getCode() == null || e.getCode().isBlank()) {
                    log.warn("Skip: cif kosong");
                    txManager.commit(status); // commit kosong biar rapih
                    continue;
                }

                String existingId = entityManager.createQuery(
                                "select c.id from Branch c where c.code = :code", String.class)
                        .setParameter("code", e.getCode())
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);

                if (existingId != null) {
                    e.setId(existingId);
                    e.setRegion(e.getRegion());
                    e.setCode(e.getCode());
                    e.setName(e.getName());
                    entityManager.merge(e);
                } else {
                    entityManager.persist(e);
                }

                entityManager.flush();
                entityManager.clear();

                txManager.commit(status); // COMMIT ✅
                processed++;

            } catch (Exception ex) {
                txManager.rollback(status); // ROLLBACK ❌ (cuma item ini)
                entityManager.clear();      // bersihin persistence context
                log.warn("Skip error name {} : {}", e.getName(), ex.getMessage());
            }
        }

        log.info("Total processed: {}", processed);
    }

}
