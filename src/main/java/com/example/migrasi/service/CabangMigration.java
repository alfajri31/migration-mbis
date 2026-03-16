package com.example.migrasi.service;

import com.example.migrasi.model.Branch;
import com.example.migrasi.util.BulkUpsertUtil;
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
public class CabangMigration {

    private static final String FILE_PATH = "data/xlsx/cabang.xlsx";
    private static final String SHEET_NAME = "Worksheet"; // nama tab excel
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
                String idCabang = getValueExcel(row, colIndex, "id_cabang");
                String rawNamaCabang = getValueExcel(row, colIndex, "nama_cabang");
                String namaCabang = Objects.requireNonNull(rawNamaCabang)
                        .replace("Kantor Cabang", "")
                        .trim();
                String codeCabang = namaCabang.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, idCabang)) {
                    continue;
                }

                /*
                 ************************************************************************************************************************
                 */
                Branch e = new Branch();
//                e.setId(kantorCabang.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_"));
                e.setId(idCabang);
                e.setName(namaCabang);
                e.setCode(codeCabang);
                entities.add(e);
            }

            log.info("Total rows read (valid): {}", entities.size());

        } catch (Exception ex) {
            log.error("Gagal membaca Excel: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca Excel: " + FILE_PATH, ex);
        }

        BulkUpsertUtil.bulkUpsert(
                entities,
                Branch.class,
                Integer.class,
                Branch::getName,
                "name",
                entityManager,
                txManager
        );

        log.info("Migration selesai.");
    }

}
