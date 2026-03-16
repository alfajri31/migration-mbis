package com.example.migrasi.service;

import com.example.migrasi.model.Customer;
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
public class SeedCustomerMigration {

    private static final String FILE_PATH = "data/xlsx/seed_customer_dev.xlsx";
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

        List<Customer> entities = new ArrayList<>();

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
                String cif = getValueExcel(row, colIndex, "CIF");
                String customerType = getValueExcel(row, colIndex, "customer_type");
                String entityType = getValueExcel(row, colIndex, "entity_type");
                String companyName = getValueExcel(row, colIndex, "company_name");
                String npwp = getValueExcel(row, colIndex, "npwp");
                String npwpNormalized = getValueExcel(row, colIndex, "npwp_normalized");
                String phone = getValueExcel(row, colIndex, "phone");
                String phoneNormalized = getValueExcel(row, colIndex, "phone_normalized");
                String email= getValueExcel(row, colIndex, "email");
                String picName= getValueExcel(row, colIndex, "pic_name");
                String address = getValueExcel(row, colIndex, "address");
                String nip = getValueExcel(row, colIndex, "nip");
                boolean isFixed = Boolean.parseBoolean(
                        String.valueOf(getValueExcel(row, colIndex, "is_fixed_assignment"))
                );

                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, nip)) {
                    continue;
                }

                /*
************************************************************************************************************************
*/
                Customer e = new Customer();
                e.setCif(cif.replaceAll("\\s+", ""));
                e.setCustomerType(customerType);
                e.setEntityType(entityType);
                e.setAddress(address);
                e.setNip(nip);
                e.setBranchId("00");
                e.setCompanyName(companyName);
                e.setNpwpNormalized(npwpNormalized);
                e.setEmail(email);
                e.setPicName(picName);
                e.setPhoneNormalized(phoneNormalized);
                e.setFixedAssignment(isFixed);
                e.setCreatedBy(UUID.fromString("e2aa6450-7fb6-4347-a875-0fc91503b172"));
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

    public void bulkUpsert(List<Customer> entities) {
        if (entities == null || entities.isEmpty()) return;

        int processed = 0;

        for (Customer e : entities) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            TransactionStatus status = txManager.getTransaction(def); // BEGIN

            try {
                //id unique key di database sendiri, id null then skip
                if (e.getCif() == null || e.getCif().isBlank()) {
                    log.warn("Skip: cif kosong");
                    txManager.commit(status); // commit kosong biar rapih
                    continue;
                }

                UUID existingId = entityManager.createQuery(
                                "select c.id from Customer c where c.cif = :cif", UUID.class)
                        .setParameter("cif", e.getCif())
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);

                if (existingId != null) {
                    e.setId(existingId);
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
                log.warn("Skip error cif {} : {}", e.getCif(), ex.getMessage());
            }
        }

        log.info("Total processed: {}", processed);
    }

}
