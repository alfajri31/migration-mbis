package com.example.migrasi.service;

import com.example.migrasi.model.Customer;
import com.example.migrasi.model.Pipeline;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.*;

import static com.example.migrasi.util.MyExcelDoc.buildColumnIndex;
import static com.example.migrasi.util.MyExcelDoc.getValueExcel;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPipelineTemplateMigration {

    private static final String FILE_PATH = "src/main/resources/excel/Template Customer & Pipeline 2026.xlsx";
    private static final String SHEET_NAME = "Pipeline"; // nama tab excel
    private static final int SKIP_ROWS = 1;                 // header row
    private static final int BATCH_SIZE = 5000;

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
                String pipelineNumber = getValueExcel(row, colIndex, "pipeline_number");
                String businessSource = getValueExcel(row, colIndex, "DIRECT");
                String businessSourceName = getValueExcel(row, colIndex, "business_source_name");
                String entityType = getValueExcel(row, colIndex, "entity_type");
                String customerLegalType = getValueExcel(row, colIndex, "customer_legal_type");
                String customerCompanyName = getValueExcel(row, colIndex, "customer_company_name");
                String customerCategory = getValueExcel(row, colIndex, "customer_category");
                String customerNpwp = getValueExcel(row, colIndex, "customer_npwp");
                String customerNik = getValueExcel(row, colIndex, "customer_nik");
                String customerNib = getValueExcel(row, colIndex, "customer_nib");
                String customerPhone =getValueExcel(row, colIndex, "customer_phone");
                String customerEmail = getValueExcel(row, colIndex, "customer_email");
                String customerPicName = getValueExcel(row, colIndex, "customer_pic_name");
                String customerPicPosition = getValueExcel(row, colIndex, "customer_pic_position");
                String customerAddress = getValueExcel(row, colIndex, "customer_address");
                String customerProvinsi = getValueExcel(row, colIndex, "customer_provinsi"); //mapping ke id provinsi
                String customerKota = getValueExcel(row, colIndex, "customer_kota"); //mapping ke id customer
                String customerKecamatan = getValueExcel(row, colIndex, "customer_company_name");  //mapping ke id kecamatan
                String customerKelurahan = getValueExcel(row, colIndex, "customer_kelurahan"); //mapping ke id kelurahan
                String postalCode = getValueExcel(row, colIndex, "customer_postal_code");
                String productName = getValueExcel(row, colIndex, "customer_company_name");//product name di mapping dulu ke product name
                String pipelineType = getValueExcel(row, colIndex, "pipeline_type");
                String currency = getValueExcel(row, colIndex, "currency");
                BigDecimal tsi = new BigDecimal(Objects.requireNonNull(getValueExcel(row, colIndex, "tsi")));
                BigDecimal totalPremi = new BigDecimal(Objects.requireNonNull(getValueExcel(row, colIndex, "total_premi")));
                boolean hasTenderProcess = Boolean.parseBoolean(getValueExcel(row, colIndex, "is_currently_in_tender"));
                String currentStageCode = getValueExcel(row, colIndex, "current_stage_code");
                OffsetDateTime estimatedClosingDate =  MyExcelDoc.toOffsetDateTime(getValueExcel(row, colIndex, "estimated_closing_date"));
                String probabilityPercentage = getValueExcel(row, colIndex, "probability_percentage");
                String notes = getValueExcel(row, colIndex, "notes");
                String branchId = getValueExcel(row, colIndex, "branch_id");
                String userId = getValueExcel(row, colIndex, "user_id");
                String currencyCode = getValueExcel(row, colIndex, "currency_code");
                /*
                 ************************************************************************************************************************
                 */
                Pipeline pipeline = new Pipeline();
                pipeline.setPipelineNumber(pipelineNumber);
                pipeline.setBusinessSource(businessSource);
                pipeline.setBusinessSourceName(businessSourceName);
                pipeline.setEntityType(entityType);
                pipeline.setCustomerLegalType(customerLegalType);
                pipeline.setCustomerCompanyName(customerCompanyName);
                pipeline.setCustomerCategory(customerCategory);
                pipeline.setCustomerNpwp(customerNpwp);
                pipeline.setCustomerNib(customerNib);
                pipeline.setCustomerNik(customerNik);
                pipeline.setCustomerPhone(customerPhone);
                pipeline.setCustomerEmail(customerEmail);
                pipeline.setCustomerPicName(customerPicName);
                pipeline.setCustomerPicPosition(customerPicPosition);
                pipeline.setCustomerAddress(customerAddress);
                pipeline.setCustomerProvinsi(customerProvinsi);
                pipeline.setCustomerKota(customerKota);
                pipeline.setCustomerKecamatan(customerKecamatan);
                pipeline.setCustomerKelurahan(customerKelurahan);
                pipeline.setCustomerPostalCode(postalCode);
                pipeline.setPipelineType(pipelineType);
                pipeline.setCurrency(currency);
                pipeline.setTsi(tsi);
                pipeline.setTotalPremi(totalPremi);
                pipeline.setHasTenderProcess(hasTenderProcess);
                pipeline.setCurrentStageCode(currentStageCode);
                pipeline.setEstimatedClosingDate(estimatedClosingDate);
                pipeline.setNotes(notes);
                pipeline.setCurrencyCode(currencyCode);

//                BulkUpsertUtil.bulkUpsert(
//                        List.of(pipeline),
//                        Pipeline.class,
//                        UUID.class,
//                        Pipeline::getNip,
//                        "nip",
//                        entityManager,
//                        txManager
//                );
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
