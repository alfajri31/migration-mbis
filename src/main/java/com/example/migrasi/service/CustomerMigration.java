package com.example.migrasi.service;

import com.example.migrasi.model.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
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

@Slf4j
@Service
public class CustomerMigration {

    private static final String FILE_PATH = "data/xlsx/customer.xlsx";
    private static final String SHEET_NAME = "Mitra"; // nama tab excel
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
                String cif = getValue(row, colIndex, "CIF");
                String nama = getValue(row, colIndex, "Nama");
                String statusKerjasama = getValue(row, colIndex, "Status Kerjasama");
                String leader = getValue(row, colIndex, "Leader");
                String member = getValue(row, colIndex, "Member");
                String alamat = getValue(row, colIndex, "Alamat");
                String bumn_non_bumn = getValue(row, colIndex, "BUMN / Non BUMN");
                String kategori = getValue(row, colIndex, "Kategori");
                String jenis_perusahaan = getValue(row, colIndex, "Jenis Perusahaan");
                String jenis_usaha = getValue(row, colIndex, "Jenis Usaha");
                String npwp = getValue(row, colIndex, "NPWP");
                String nama_customer = getValue(row, colIndex, "Nama Customer");
                String jabatan_customer = getValue(row, colIndex, "Jabatan Customer");
                String hp = getValue(row, colIndex, "HP");
                String email = getValue(row, colIndex, "Email");
                String foto = getValue(row, colIndex, "Foto");
                String sumber_bisnis = getValue(row, colIndex, "Sumber Bisnis");
                String nama_agen = getValue(row, colIndex, "Nama Agen");
                String nama_broker = getValue(row, colIndex, "Nama Broker");
                String divisi = getValue(row, colIndex, "Divisi");
                String kanwil = getValue(row, colIndex, "Kanwil");
                String kantor_cabang = getValue(row, colIndex, "Kantor Cabang");
                String username = getValue(row, colIndex, "Username");
                String nama_rm = getValue(row, colIndex, "Nama RM");
                String broker_lokal_broker_luar_daerah = getValue(row, colIndex, "Broker Lokal/Broker Luar Daerah");
                String bisnis_lokal_bisnis_luar_daerah = getValue(row, colIndex, "Bisnis Lokal/Bisnis Luar Daerah");
                String generator = getValue(row, colIndex, "Generator");

                //id null then skip
                if (cif == null || cif.isBlank() || jenis_perusahaan == null || jenis_perusahaan.isBlank()) {
                    log.warn("Skip row {}: CIF kosong", rowNumber);
                    continue;
                }

                /*
************************************************************************************************************************
*/
                Customer e = new Customer();
                e.setCif(cif.replaceAll("\\s+", ""));
                e.setNamaCustomer(nama);
                e.setNamaRm(nama_rm);
                e.setNpwp(npwp);
                e.setAddress(alamat);
                e.setFoto(foto);
                e.setBentukCustomer(jenis_perusahaan.toUpperCase());
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



    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            if (cell == null) continue;

            String name = cell.getStringCellValue();
            if (name == null) continue;

            String key = name.trim().toUpperCase();
            if (!key.isEmpty()) {
                map.put(key, cell.getColumnIndex());
            }
        }
        log.info("Detected columns: {}", map.keySet());
        return map;
    }

    private String getValue(Row row, Map<String, Integer> colIndex, String columnName) {
        Integer idx = colIndex.get(columnName.trim().toUpperCase());
        if (idx == null) return null;

        Cell cell = row.getCell(idx);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> trimToNull(cell.getStringCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                long lv = (long) v;
                yield (v == lv) ? String.valueOf(lv) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> trimToNull(cell.getStringCellValue());
                case NUMERIC -> {
                    double v = cell.getNumericCellValue();
                    long lv = (long) v;
                    yield (v == lv) ? String.valueOf(lv) : String.valueOf(v);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> null;
            };
            case BLANK, _NONE, ERROR -> null;
        };
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
