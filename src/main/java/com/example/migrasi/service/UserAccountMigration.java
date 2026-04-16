package com.example.migrasi.service;

import com.example.migrasi.model.*;
import com.example.migrasi.repository.DivisionRepository;
import com.example.migrasi.repository.RoleRepository;
import com.example.migrasi.repository.UserMappingRepository;
import com.example.migrasi.util.BulkUpsertUtil;
import com.example.migrasi.util.JpaUtil;
import com.example.migrasi.util.Normalizing;
import com.example.migrasi.util.RowSkipUtil;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.example.migrasi.util.MyExcelDoc.buildColumnIndex;
import static com.example.migrasi.util.MyExcelDoc.getValueExcel;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountMigration {
    private final RoleRepository roleRepository;
    private final DivisionRepository divisionRepository;

    private final UserMappingRepository userMappingRepository;

    private final BranchRepository branchRepository;

    private static final String FILE_PATH = "data/xlsx/user-mbis.xlsx";
    private static final String SHEET_NAME = "USER"; // nama tab excel
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
                String nip = getValueExcel(row, colIndex, "nip");
                String nama = getValueExcel(row, colIndex, "nama");
                String email = getValueExcel(row, colIndex, "email");
                String cabang = getValueExcel(row, colIndex, "cabang");
                String divisi = getValueExcel(row, colIndex, "divisi");
                String role = getValueExcel(row, colIndex, "role");
                String note = getValueExcel(row, colIndex, "note");

                UUID existingId = JpaUtil.findField(entityManager, User.class,"id","nip",nip, UUID.class);

                User user = new User();

                user.setId(UUID.randomUUID());
                user.setNip(nip);

                user.setFullName(nama);

                user.setEmail(email);



                Optional<Division> division = divisionRepository.findByNameIgnoreCase(divisi);

                if(division.isEmpty()) {
                    continue;
                }

                user.setDivisionCode(division.get().getId());

                if(cabang!=null) {
                    if(cabang.equalsIgnoreCase("jakarta kemayoran")) {
                        cabang = "kemayoran";
                    }
                    if(cabang.equalsIgnoreCase("Pangkalpinang")) {
                        cabang = "pangkal pinang";
                    }
                }
                Optional<Branch> branch = branchRepository.findByNameIgnoreCase(cabang);

                if(branch.isPresent()) {

                    int result = BulkUpsertUtil
                            .bulkUpsert(
                                    List.of(user),
                                    User.class,
                                    UUID.class,
                                    User::getNip,
                                    "nip",
                                    entityManager,
                                    txManager);

                    Optional<Role> role1 = roleRepository.findByIdIgnoreCase(role);

                    UserMapping userMapping = new UserMapping();
                    UUID userId= JpaUtil.findField(entityManager, User.class,"id","nip",nip, UUID.class);
                    userMapping.setUserId(userId);
                    userMapping.setBranchId(branch.get().getId());
                    userMapping.setDivisionId(division.get().getId());
                    userMapping.setRole(role1.get());
                    userMapping.setAssignedBy(user.getId());

                    int result2 = BulkUpsertUtil
                            .bulkUpsert(
                                    List.of(userMapping),
                                    UserMapping.class,
                                    UUID.class,
                                    UserMapping::getUserId,
                                    "userId",
                                    entityManager,
                                    txManager);

                    log.info("Total rows read (valid): {}", entities.size());

                }
            }



        } catch (Exception ex) {
            log.error("Gagal membaca Excel: {}", FILE_PATH, ex);
            throw new RuntimeException("Gagal membaca Excel: " + FILE_PATH, ex);
        }

        log.info("Migration selesai.");
    }
}
