package com.example.migrasi.service;

import com.example.migrasi.model.*;
import com.example.migrasi.repository.DivisionRepository;
import com.example.migrasi.repository.RoleRepository;
import com.example.migrasi.repository.UserMappingRepository;
import com.example.migrasi.util.BulkUpsertUtil;
import com.example.migrasi.util.JpaUtil;
import com.example.migrasi.util.MyExcelDoc;
import com.example.migrasi.util.RowSkipUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

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
public class UserRmMigration {

    private static final String FILE_PATH = "data/csv/mbisuser.csv";
    private static final int SKIP_ROWS = 0;   // header
    private static final int BATCH_SIZE = 500;
    private static final int IDX_BRANCH_ID = 0;
    private static final int IDX_NIP = 1;
    private static final int IDX_DIVISI_ID = 2;
    private static final int IDX_DIVISI_NAMA = 3;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager txManager;

    @Transactional
    public void migrate() {
        log.info("Running kode pos migration from CSV: {}", FILE_PATH);

        List<RUserApplication> entities = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Path.of(FILE_PATH)), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = SKIP_ROWS;

            while ((line = br.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) continue;

                String[] cols = splitCsvSimple(line);

                UUID idRegApplication= UUID.fromString("3ce05f2e-4858-4170-9433-b2a8851e37fc");

                String idbranchId = MyExcelDoc.getValueCsv(cols, IDX_BRANCH_ID);
                String idNip  = MyExcelDoc.getValueCsv(cols, IDX_NIP);
                String idDivisiId  = MyExcelDoc.getValueCsv(cols, IDX_DIVISI_ID);
                String idDivisiNama  = MyExcelDoc.getValueCsv(cols, IDX_DIVISI_NAMA);

                UUID userExistingId = JpaUtil.findField(entityManager, RUser.class,"id","nip",idNip, UUID.class);

                if(userExistingId==null) {
                    //
                }

                if(userExistingId!=null) {
                    UUID ldapUserExist = JpaUtil.findFieldWithTwoWhere(entityManager,RUserApplication.class,
                            "idApplication",
                            "idUser",
                            userExistingId,
                            "idApplication",
                            idRegApplication,
                            UUID.class);
                    if(ldapUserExist!=null) {
                        RUserApplication userApplication = new RUserApplication();
                        userApplication.setIdUser(userExistingId);
                        userApplication.setIdApplication(idRegApplication);
                        log.info("data nya nip {}", idNip);
                        //save disini di r_user_application
                    }
                }
            }

            log.info("Total rows read (valid): {}", entities.size());

        } catch (Exception ex) {
            log.error("Gagal membaca CSV: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca CSV: " + FILE_PATH, ex);
        }

        log.info("Province migration selesai.");
    }

    // Split sederhana: cukup untuk CSV yang tidak punya koma di dalam quote.
    // Kalau CSV kamu kompleks (ada koma dalam quotes), bilang ya—aku kasih parser yang handle quotes.
    private String[] splitCsvSimple(String line) {
        return line.split(",", -1);
    }

}
