package com.example.migrasi;

import com.example.migrasi.service.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@AllArgsConstructor
@Slf4j
public class MigrasiApplication implements CommandLineRunner {

    private static final String[] MIGRATION_NAMES = {
            "provinsi"

    };

    private final SeedCustomerMigration seedCustomerMigration;
    private final CustomerMigration customerMigration;
    private final ProvinceMigration provinceMigration;
    private final RegencyMigration regencyMigration;
    private final DistrictMigration districtMigration;
    private final VillagesMigration villagesMigration;
    private final CabangMigration cabangMigration;
    private final DivisiMigration divisionMigration;
    private final KanwilMigration kanwilMigration;
    private final PostalCodeMigration postalCodeMigration;

    public static void main(String[] args) {
        SpringApplication.run(MigrasiApplication.class, args);
    }

    @Override
    public void run(String... args) {

        for (String migrationName : MIGRATION_NAMES) {

            switch (migrationName.toLowerCase()) {
                case "customer" -> {
                    log.info("Running Customer Migration...");
                    customerMigration.migrate();
                }

                case "provinsi" -> {
                    log.info("Running Province Migration...");
                    provinceMigration.migrate();
                }

                case "kota" -> {
                    log.info("Running Regency Migration...");
                    regencyMigration.migrate();
                }

                case "kecamatan" -> {
                    log.info("Running district Migration...");
                    districtMigration.migrate();
                }

                case "kelurahan" -> {
                    log.info("Running district Migration...");
                    villagesMigration.migrate();
                }

                case "kodepos" -> {
                    log.info("Running Seed kode pos Migration...");
                    postalCodeMigration.migrate();
                }

                case "cabang" -> {
                    log.info("Running cabang Migration...");
                    cabangMigration.migrate();
                }

                case "divisi" -> {
                    log.info("Running division Migration...");
                    divisionMigration.migrate();
                }

                case "kanwil" -> {
                    log.info("Running kanwil Migration...");
                    kanwilMigration.migrate();
                }

                case "seed-customer" -> {
                    log.info("Running Seed Customer Migration...");
                    seedCustomerMigration.migrate();
                }

                default -> {
                    log.info("Unknown migration: {} ", migrationName);
                    throw new RuntimeException("Unknown migration");
                }
            }
        }
        System.exit(0); // optional
    }
}
