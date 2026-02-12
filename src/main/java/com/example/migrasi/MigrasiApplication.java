package com.example.migrasi;

import com.example.migrasi.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class MigrasiApplication implements CommandLineRunner {

    // 🔥 Bisa isi banyak sekaligus
    private static final String[] MIGRATION_NAMES = {
            "kecamatan"
    };

    private final CustomerMigration customerMigration;
    private final ProvinceMigration provinceMigration;
    private final RegencyMigration regencyMigration;
    private final DistrictMigration districtMigration;
    private final VillagesMigration villagesMigration;

    public MigrasiApplication(CustomerMigration customerMigration,
                              ProvinceMigration provinceMigration,
                              RegencyMigration regencyMigration,
                              DistrictMigration districtMigration,
                              VillagesMigration villagesMigration) {
        this.customerMigration = customerMigration;
        this.provinceMigration = provinceMigration;
        this.regencyMigration = regencyMigration;
        this.districtMigration = districtMigration;
        this.villagesMigration = villagesMigration;
    }

    public static void main(String[] args) {
        SpringApplication.run(MigrasiApplication.class, args);
    }

    @Override
    public void run(String... args) {

        for (String migrationName : MIGRATION_NAMES) {

            switch (migrationName.toLowerCase()) {
                case "customer" -> {
                    System.out.println("Running Customer Migration...");
                    customerMigration.migrate();
                }

                case "provinsi" -> {
                    System.out.println("Running Province Migration...");
                    provinceMigration.migrate();
                }

                case "kota" -> {
                    System.out.println("Running Regency Migration...");
                    regencyMigration.migrate();
                }

                case "kecamatan" -> {
                    System.out.println("Running district Migration...");
                    districtMigration.migrate();
                }

                case "kelurahan" -> {
                    System.out.println("Running district Migration...");
                    villagesMigration.migrate();
                }

                default -> {
                    System.out.println("Unknown migration: " + migrationName);
                }
            }
        }

        System.exit(0); // optional
    }
}
