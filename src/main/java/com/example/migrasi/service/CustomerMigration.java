package com.example.migrasi.service;

import com.example.migrasi.model.Customer;
import com.example.migrasi.util.BulkUpsertUtil;
import com.example.migrasi.util.Normalizing;
import com.example.migrasi.util.RowSkipUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
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
                String cif = getValueExcel(row, colIndex, "CIF");
                String nama = getValueExcel(row, colIndex, "Nama");
                String statusKerjasama = getValueExcel(row, colIndex, "Status Kerjasama");
                String leader = getValueExcel(row, colIndex, "Leader");
                String member = getValueExcel(row, colIndex, "Member");
                String hp = getValueExcel(row, colIndex, "HP");
                String alamat = getValueExcel(row, colIndex, "Alamat");
                String bumn_non_bumn = getValueExcel(row, colIndex, "BUMN / Non BUMN");
                String kategori = getValueExcel(row, colIndex, "Kategori");
                String jenis_perusahaan = getValueExcel(row, colIndex, "Jenis Perusahaan");
                String jenis_usaha = getValueExcel(row, colIndex, "Jenis Usaha");
                String npwp = getValueExcel(row, colIndex, "NPWP");
                String nama_customer = getValueExcel(row, colIndex, "Nama Customer");
                String jabatan_customer = getValueExcel(row, colIndex, "Jabatan Customer");
                String email = getValueExcel(row, colIndex, "Email");
                String foto = getValueExcel(row, colIndex, "Foto");
                String sumber_bisnis = getValueExcel(row, colIndex, "Sumber Bisnis");
                String nama_agen = getValueExcel(row, colIndex, "Nama Agen");
                String nama_broker = getValueExcel(row, colIndex, "Nama Broker");
                String divisi = getValueExcel(row, colIndex, "Divisi");
                String kanwil = getValueExcel(row, colIndex, "Kanwil");
                String kantor_cabang = getValueExcel(row, colIndex, "Kantor Cabang");
                String username = getValueExcel(row, colIndex, "Username");
                String nama_rm = getValueExcel(row, colIndex, "Nama RM");
                String broker_lokal_broker_luar_daerah = getValueExcel(row, colIndex, "Broker Lokal/Broker Luar Daerah");
                String bisnis_lokal_bisnis_luar_daerah = getValueExcel(row, colIndex, "Bisnis Lokal/Bisnis Luar Daerah");
                String generator = getValueExcel(row, colIndex, "Generator");

                //id unique key di excel, id null then skip
                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, cif)) {
                    continue;
                }
                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, kantor_cabang)) {
                    continue;
                }
                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, jenis_perusahaan)) {
                    continue;
                }
                //id null then skip
                if (RowSkipUtil.skipIdField(rowNumber, bumn_non_bumn)) {
                    continue;
                }
                /*
************************************************************************************************************************
*/
                Customer e = new Customer();
                e.setCif(cif.replaceAll("\\s+", ""));
                e.setCompanyName(nama);
                e.setPicName(nama_rm);
                e.setAddress(alamat);
                e.setFotoFile(foto);
                e.setEntityType(bumn_non_bumn.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_"));
                e.setCustomerType(statusKerjasama);
                e.setNip(username);
                e.setEmail(email);
                e.setPicPhone(Normalizing.normalizePhone(hp));
                e.setPhoneNormalized(Normalizing.normalizePhone(hp));
                if((npwp != null ? npwp.length() : 0) > 8) {
                    e.setNpwp(npwp);
                    e.setNpwpNormalized(Normalizing.normalizeNpwp(npwp));
                }
                String kantorCabang = kantor_cabang.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
                String existingId = entityManager.createQuery(
                                "select c.id from Branch c where c.code = :code", String.class)
                        .setParameter("code", kantorCabang)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);
                e.setBranchId(existingId);
                e.setCreatedBy(UUID.fromString("e2aa6450-7fb6-4347-a875-0fc91503b172"));
                entities.add(e);
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
                Customer::getCif,
                "cif",
                entityManager,
                txManager
        );

        log.info("Migration selesai.");
    }

}
