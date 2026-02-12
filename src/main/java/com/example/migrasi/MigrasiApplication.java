package com.example.migrasi;

import com.example.migrasi.service.CustomerMigration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MigrasiApplication implements CommandLineRunner {

    // 🔥 Hardcode di sini
    private static final String MIGRATION_NAME = "customer";

    private final CustomerMigration customerMigration;

    public MigrasiApplication(CustomerMigration customerMigration) {
        this.customerMigration = customerMigration;
    }

    public static void main(String[] args) {
        SpringApplication.run(MigrasiApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if ("customer".equalsIgnoreCase(MIGRATION_NAME)) {
            customerMigration.migrate();
        }
        System.exit(0); // optional biar langsung selesai
    }
}
