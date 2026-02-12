package com.example.migrasi;

import com.example.migrasi.service.CustomerMigration;
import com.example.migrasi.service.ProvinceMigration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MigrasiApplication implements CommandLineRunner {

    // 🔥 Hardcode di sini
    private static final String MIGRATION_NAME = "provinsi";

    private final CustomerMigration customerMigration;
    private final ProvinceMigration provinceMigration;

    public MigrasiApplication(CustomerMigration customerMigration, ProvinceMigration provinceMigration) {
        this.customerMigration = customerMigration;
        this.provinceMigration = provinceMigration;
    }

    public static void main(String[] args) {
        SpringApplication.run(MigrasiApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if ("customer".equalsIgnoreCase(MIGRATION_NAME)) {
            customerMigration.migrate();
        }
        else if ("provinsi".equalsIgnoreCase(MIGRATION_NAME)) {
            provinceMigration.migrate();
        }
        System.exit(0); // optional biar langsung selesai
    }
}
