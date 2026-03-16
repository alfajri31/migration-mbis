package com.example.migrasi;

import com.example.migrasi.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MigrasiApplication implements CommandLineRunner {

    // 🔥 Bisa isi banyak sekaligus
    private static final String[] MIGRATION_NAMES = {
            "seed-customer"

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


    public MigrasiApplication(CustomerMigration customerMigration,
                              ProvinceMigration provinceMigration,
                              RegencyMigration regencyMigration,
                              DistrictMigration districtMigration,
                              VillagesMigration villagesMigration,
                              CabangMigration cabangMigration,
                              DivisiMigration divisionMigration,
                              KanwilMigration kanwilMigration,
                              SeedCustomerMigration seedCustomerMigration,
                              PostalCodeMigration postalCodeMigration) {
        this.customerMigration = customerMigration;
        this.provinceMigration = provinceMigration;
        this.regencyMigration = regencyMigration;
        this.districtMigration = districtMigration;
        this.villagesMigration = villagesMigration;
        this.cabangMigration = cabangMigration;
        this.divisionMigration = divisionMigration;
        this.kanwilMigration = kanwilMigration;
        this.seedCustomerMigration = seedCustomerMigration;
        this.postalCodeMigration = postalCodeMigration;
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

                case "kodepos" -> {
                    System.out.println("Running Seed kode pos Migration...");
                    postalCodeMigration.migrate();
                }

                case "cabang" -> {
                    System.out.println("Running cabang Migration...");
                    cabangMigration.migrate();
                }

                case "divisi" -> {
                    System.out.println("Running division Migration...");
                    divisionMigration.migrate();
                }

                case "kanwil" -> {
                    System.out.println("Running kanwil Migration...");
                    kanwilMigration.migrate();
                }

                case "seed-customer" -> {
                    System.out.println("Running Seed Customer Migration...");
                    seedCustomerMigration.migrate();
                }

                default -> {
                    System.out.println("Unknown migration: " + migrationName);
                    throw new RuntimeException("Unknown migration");
                }
            }
        }
        System.exit(0); // optional
    }
}
