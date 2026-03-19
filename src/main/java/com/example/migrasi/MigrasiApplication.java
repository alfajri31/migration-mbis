package com.example.migrasi;

import com.example.migrasi.AI.AiBaseKnowledgeService;
import com.example.migrasi.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringBootApplication
@AllArgsConstructor
@Slf4j
public class MigrasiApplication implements CommandLineRunner {

    private static final String[] MIGRATION_NAMES = {
            ""

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
    private final DatabaseExportService databaseExportService;
    private final AiBaseKnowledgeService aiBaseKnowledgeService;
    private List<String> detailsKnowledge;
    private Map<String,List<String>> basesKnowledge;

    public static void main(String[] args) {
        SpringApplication.run(MigrasiApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        String jsonString = databaseExportService.exportDatabase(Set.of("log_table"));

        HashMap<String,List<String>> map = new HashMap<>();

        map.put("database", addDetailsKnowledge(jsonString));

        basesKnowledge.putAll(map);

        aiBaseKnowledgeService.processKnowledgeBase(
                basesKnowledge.get("database"));

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

    private List<String> addDetailsKnowledge(String jsonString) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        List<Map<String, Object>> list =
                mapper.readValue(jsonString, new TypeReference<>() {});

        for (Map<String, Object> item : list) {
            detailsKnowledge.add(mapper.writeValueAsString(item));
        }

        return detailsKnowledge;
    }


}
