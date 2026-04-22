package com.example.migrasi.service;

import com.example.migrasi.model.Customer;
import com.example.migrasi.model.RUser;
import com.example.migrasi.model.RUserApplication;
import com.example.migrasi.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.example.migrasi.util.MyExcelDoc.buildColumnIndex;
import static com.example.migrasi.util.MyExcelDoc.getValueExcel;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientCustTemplateMigration {

    private static final String FILE_PATH = "src/main/resources/excel/Template Customer & Pipeline 2026.xlsx";
    private static final String SHEET_NAME = "Customer"; // nama tab excel
    private static final int SKIP_ROWS = 1;                 // header row

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
                String companyName = getValueExcel(row, colIndex, "company_name ");
                String cif = getValueExcel(row, colIndex, "cif"); // perlu normalisasi
                String entityType = getValueExcel(row, colIndex, "entity_type"); //bumn or non_bumn
                String legalType = getValueExcel(row, colIndex, "npwp");
                String npwp = getValueExcel(row, colIndex, "npwpw"); //kalo kosong atau tidak format npwp kasih prefix 000 aja
                String nik = getValueExcel(row, colIndex, "nik");
                String nib = getValueExcel(row, colIndex, "nib");
                String phone = getValueExcel(row, colIndex, "phone");
                String email = getValueExcel(row, colIndex, "email");
                String picName = getValueExcel(row, colIndex, "picName");
                String picPosition = getValueExcel(row, colIndex, "pic_position");
                String address = getValueExcel(row, colIndex, "address");
                String postalCode = getValueExcel(row, colIndex, "postalCode");

                /*
                 ************************************************************************************************************************
                 */
                Customer e = new Customer();
                e.setCompanyName(companyName);
                e.setCif(cif.replaceAll("\\s+", ""));
                e.setCustomerType(legalType);
                e.setCustomerNik(nik);
                e.setCustomerNib(nib);
                e.setPicPhone(phone);
                e.setPicName(picName);
                e.setPicPosition(picPosition);
                e.setAddress(address);
                e.setPostalCode(postalCode);
                e.setEntityType(entityType);
                e.setEmail(email);
                e.setNpwp(npwp);
                e.setCreatedBy(UUID.fromString("e2aa6450-7fb6-4347-a875-0fc91503b172"));
                BulkUpsertUtil.bulkUpsert(
                        List.of(e),
                        Customer.class,
                        UUID.class,
                        Customer::getNip,
                        "npwp",
                        entityManager,
                        txManager
                );
            }
            log.info("Total rows read (valid): {}", entities.size());
        } catch (Exception ex) {
            log.error("Gagal membaca Excel: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca Excel: " + FILE_PATH, ex);
        }
        BulkUpsertUtil.bulkUpsert(
                entities,
                Customer.class,
                UUID.class,
                Customer::getNip,
                "nip",
                entityManager,
                txManager
        );

        log.info("Migration selesai.");
    }

}
