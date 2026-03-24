package com.example.migrasi.AI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Service
@Slf4j
public class AiBaseKnowledgeService {

    @Value("${agent.host.url}")
    private String url;

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    @Value("${ai.model.context.base.knowledge}")
    private int contextWindow;

    private final static int reservedTokens= 500;
    private final RestTemplate restTemplate = new RestTemplate();

    // =========================
    // MAIN ENTRY
    // =========================
    public void processKnowledgeBase(Map<String, List<String>> data, String type) throws Exception {

        int safeContextWindow = contextWindow - reservedTokens;

        if (isTokenOverflow(data, safeContextWindow)) {
            log.warn("Can't be proceed: tokens will be overflow");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = mapPhase(data, mapper, safeContextWindow);

        Map<String, Object> structuredData = buildStructuredData(data, partialSummaries, mapper);

        int autoBatch = calculateBatchCountSmart(structuredData);

        if (autoBatch > 1) {
            processBatch(structuredData, mapper, type,autoBatch);
        } else {
            processSingle(structuredData, mapper, type);
        }
    }

    private int calculateBatchCountSmart(Map<String, Object> structuredData) {

        int totalChars = 0;
        int totalItems = 0;

        for (Map.Entry<String, Object> entry : structuredData.entrySet()) {
            List<?> list = (List<?>) entry.getValue();

            for (Object o : list) {
                int len = o.toString().length();
                totalChars += len;
                totalItems++;
            }
        }

        if (totalItems == 0) return 1;

        // rata-rata panjang item
        double avgSize = (double) totalChars / totalItems;

        // estimasi ideal items per batch (dinamis)
        int itemsPerBatch = (int) Math.sqrt(totalItems);

        // estimasi char per batch
        double estimatedBatchSize = itemsPerBatch * avgSize;

        // jumlah batch
        int batchCount = (int) Math.ceil((double) totalChars / estimatedBatchSize);

        return Math.max(batchCount, 1);
    }
    // =========================
    // TOKEN VALIDATION
    // =========================
    private boolean isTokenOverflow(Map<String, List<String>> data, int safeContextWindow) {

        int totalChunkCounts = 0;
        int totalLength = 0;

        for (List<String> list : data.values()) {
            if (list.size() < safeContextWindow) {
                totalLength += list.size();
            }
            totalChunkCounts += list.size();
        }

        int estimationSummaryTokens = totalChunkCounts * (totalLength * 3);

        return estimationSummaryTokens >= safeContextWindow;
    }

    // =========================
    // MAP PHASE
    // =========================
    private List<String> mapPhase(Map<String, List<String>> data,
                                  ObjectMapper mapper,
                                  int safeContextWindow) throws Exception {

        log.info("START MAP PHASE (per chunk processing)");

        List<String> partialSummaries = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();
            List<String> chunks = entry.getValue();

            log.info("total all size chunks: {}", chunks.size());

            for (String chunk : chunks) {

                String jsonChunk = mapper.writeValueAsString(
                        Map.of(key, mapper.readValue("[" + chunk + "]", List.class))
                );

                int estimatedTokens = estimateTokens(jsonChunk);

                log.warn("Chunk tokens={}", estimatedTokens);

                if (estimatedTokens > safeContextWindow) {
                    log.warn("Chunk over context window, skipping... tokens={}", estimatedTokens);
                    continue;
                }

                partialSummaries.add(jsonChunk);
            }
        }

        return partialSummaries;
    }

    private Map<String, Object> buildStructuredData(Map<String, List<String>> originalData,
                                                    List<String> partialSummaries,
                                                    ObjectMapper mapper) {

        List<Object> parsed = partialSummaries.stream()
                .map(s -> {
                    try {
                        return mapper.readValue(s, Object.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        Map<String, List<Object>> merged = new HashMap<>();

        for (Object obj : parsed) {
            Map<String, Object> map = (Map<String, Object>) obj;

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .addAll(list);
                }
            }
        }

        Map<String, Object> structuredData = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : originalData.entrySet()) {

            String key = entry.getKey();
            List<Object> data = merged.getOrDefault(key, new ArrayList<>());

            // 🔥 SPECIAL HANDLING schema_db
            if ("schema_db".equals(key)) {
                structuredData.put(key, transformSchemaDb(data));
            } else {
                structuredData.put(key, data);
            }
        }

        return structuredData;
    }

    private List<Map<String, Object>> transformSchemaDb(List<Object> schemaList) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object obj : schemaList) {

            Map<String, Object> tableMap = (Map<String, Object>) obj;

            String tableName = (String) tableMap.get("table");

            Map<String, Object> ddl = (Map<String, Object>) tableMap.get("ddl");
            List<Map<String, Object>> columns =
                    (List<Map<String, Object>>) ddl.get("columns");

            List<String> fields = columns.stream()
                    .map(col -> (String) col.get("name"))
                    .toList();

            List<Map<String, Object>> rows =
                    (List<Map<String, Object>>) tableMap.get("rows");

            Map<String, Object> formatted = new LinkedHashMap<>();
            formatted.put("table", tableName);
            formatted.put("schema_columns", fields);
            formatted.put("sample_data", rows);

            result.add(formatted);
        }

        return result;
    }

    // =========================
    // BATCH PROCESSING
    // =========================
    private void processBatch(Map<String, Object> structuredData,
                                ObjectMapper mapper,
                                String type,
                                int dataBatch) throws Exception {

        Map<String, List<List<?>>> batchedData = splitBatches(structuredData,dataBatch);

        List<String> keys = new ArrayList<>(batchedData.keySet());

        if (keys.size() < 2) {
            throw new IllegalStateException("Minimal harus ada 2 key untuk kombinasi");
        }

        String key1 = keys.get(0);

        String key2 = keys.get(1);

        List<List<?>> key1Batches = batchedData.get(key1);

        List<List<?>> key2Batches = batchedData.get(key2);

        int totalCombination = key1Batches.size() * key2Batches.size();

        log.info("TOTAL KOMBINASI: {}", totalCombination);

        for (int i = 0; i < key1Batches.size(); i++) {

            for (int j = 0; j < key2Batches.size(); j++) {

                Map<String, Object> batchData = Map.of(
                        key1, key1Batches.get(i),
                        key2, key2Batches.get(j)
                );

                log.info("Combine: {} batch {} WITH {} batch {}", key1, i + 1, key2, j + 1);

                callSummary(
                        mapper.writeValueAsString(batchData),
                        type
                );

            }
        }
    }

    // =========================
    // SPLIT BATCH
    // =========================
    private Map<String, List<List<?>>> splitBatches(Map<String, Object> structuredData,int dataBatch) {

        Map<String, List<List<?>>> batchedData = new LinkedHashMap<>();

        int totalSize = ((List<?>) structuredData.values().iterator().next()).size();

        int batchSize = (int) Math.ceil((double) totalSize / dataBatch);

        for (Map.Entry<String, Object> entry : structuredData.entrySet()) {

            List<?> fullList = (List<?>) entry.getValue();
            List<List<?>> batches = new ArrayList<>();

            for (int i = 0; i < dataBatch; i++) {

                int start = i * batchSize;

                int end = Math.min(start + batchSize, fullList.size());

                if (start >= fullList.size()) break;

                batches.add(fullList.subList(start, end));
            }

            batchedData.put(entry.getKey(), batches);
        }

        return batchedData;
    }

    // =========================
    // SINGLE PROCESS
    // =========================
    private void processSingle(Map<String, Object> structuredData,
                                 ObjectMapper mapper,
                                 String type) throws Exception {

        callSummary(
                mapper.writeValueAsString(structuredData),
                type
        );

    }

    private void callSummary(String dataJson, String type) {

        ObjectMapper mapper  = new ObjectMapper();

        String systemPrompt;

        if(type.equals("fe2be")) {

            systemPrompt = """
                        OUTPUT HARUS JSON SAJA TANPA TAMBAHAN PENJELASAN TEKS TAMBAHAN LAGI.
                        
                        SUMBER DATA:
                        client_data sebagai pusat key entry nya
                        
                        LARANGAN:
                        LARANGAN RESPONS USER KETIKA DATA DARI ROLE USER DI KOLOM CHAT TIDAK ADA!!
                            
                        PERAN:
                        AI untuk mapping field kosong dari frontend ke schema database.
                        
                        TUJUAN:
                        Cari field schema_db yang paling cocok untuk mengisi field frontend yang kosong
                        
                        KRITERIA:
                        - Nama field
                        - Value field
                        
                        FORMAT OUTPUT:
                        [
                            {
                              "fe_field_empty": "...",
                              "existing_table_name": "...",
                              "existing_table_field_name": "...",
                              "field_existing": "high exist | medium exist | low exist",
                              "reason": "..."
                            }
                        ]
                        
                        RULE:
                        - Jika tidak relevan → skip (jangan dipaksa)
                        - Hanya JSON
                        """;

            List<String> userPrompts = buildPromptFe2Be(dataJson);

            for (String prompt : userPrompts) {

                Map<String, Object> request = new HashMap<>();

                List<Map<String, Object>> messages = new ArrayList<>();

                messages.add(Map.of("role", "system", "content", systemPrompt));

                messages.add(Map.of("role", "user", "content", prompt));

                request.put("model", aiModel);

                request.put("messages", messages);

                request.put("temperature", 0);

                request.put("stream", false);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

                Map body = response.getBody();

                Map message = (Map) body.get("message");

                String content = message.get("content").toString();

                try {
                    JsonNode node = mapper.readTree(content);
                    saveToFile(node, type);
                } catch (Exception e) {
                    log.info("error json node {}", e.getMessage());
                }
            }

        }

        if(type.equals("client2be")) {

            systemPrompt = """
                    OUTPUT HARUS BERUPA JSON VALID SAJA. TANPA PENJELASAN TEKS TAMBAHAN LAGI.
                                        
                    PERAN:
                    AI bertugas mencari PATH JSON di schema_db berdasarkan array dari property client_columns.
                    AI bertugas mencari semantik berdasarkan array di client_columns
                                        
                    TUJUAN:
                                        
                    * Dari client_columns → cari field yang paling cocok di schema_db.schema_columns
                                        
                    ALUR WAJIB:
                                        
                    1. BACA client_columns
                    2. TELUSURI SELURUH schema_db.schema_columns
                    3. TEMUKAN kandidat yang SAMA SECARA SEMANTIK
                                        
                    ATURAN KERAS:
                                        
                    * HANYA boleh memilih dari schema_db
                    * DILARANG membuat nama field/table baru
                    * JIKA tidak yakin → WAJIB SKIP
                                        
                    KRITERIA MATCH:
                                        
                    * Exact match (nama sama) → HIGH
                    * Sinonim jelas → MEDIUM
                    * kata sangan berbeda tetap makna hampir sama → LOW
                                        
                    JIKA TIDAK ADA YANG COCOK BAIK DARI SEGI MAKNA:
                    SKIP
                                       
                                        
                    FORMAT OUTPUT:
                    [
                        {
                            "client_column_name": "...",
                            "match_found": true | false,
                            "schema_db_table_name": "schema_db.table",
                            "schema_db_column_name": "...",
                            "json_path_in_schema_db": "schema_db.schema_columns",
                            "similarity_confidence": "high | medium | low",
                            "reason_confidence": ""
                        }
                    ]           \s
                    """;

            List<String> userPrompts = buildPromptClient2Be(dataJson);

            for (String prompt : userPrompts) {

                Map<String, Object> request = new HashMap<>();

                List<Map<String, Object>> messages = new ArrayList<>();

                messages.add(Map.of("role", "system", "content", systemPrompt));

                messages.add(Map.of("role", "user", "content", prompt));

                request.put("model", aiModel);

                request.put("messages", messages);

                request.put("temperature", 0);

                request.put("stream", false);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

                Map body = response.getBody();

                Map message = (Map) body.get("message");

                String content = message.get("content").toString();

                try {
                    JsonNode node = mapper.readTree(content);
                    saveToFile(node, type);
                } catch (Exception e) {
                    log.info("error json node {}", e.getMessage());
                }
            }
        }
    }

    private void saveToFile(JsonNode node, String type) throws Exception {

        String folderPath = "summary";
        Path directory = Paths.get(folderPath);

        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        String fileName = type.toLowerCase() + ".html";

        Path filePath = directory.resolve(fileName);

        boolean fileExists = Files.exists(filePath);

        StringBuilder finalContent = new StringBuilder();

        // HANDLE ARRAY
        if (!node.isArray()) {
            log.warn("Node bukan array, skip");
            return;
        }

        // ambil keys dari object pertama
        JsonNode firstObj = node.get(0);
        if (firstObj == null || !firstObj.isObject()) {
            log.warn("Array kosong / invalid");
            return;
        }

        List<String> keys = new ArrayList<>();
        firstObj.fieldNames().forEachRemaining(keys::add);

        // HEADER hanya dibuat sekali
        if (!fileExists) {

            StringBuilder header = new StringBuilder();
            StringBuilder filterRow = new StringBuilder();

            header.append("<tr>");
            filterRow.append("<tr>");

            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);

                header.append("<th>").append(key).append("</th>");
                filterRow.append("<th><input type='text' onkeyup='filterTable(this, ")
                        .append(i)
                        .append(")' placeholder='Filter ").append(key).append("'></th>");
            }

            header.append("</tr>");
            filterRow.append("</tr>");

            finalContent.append("""
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Table</title>
<style>
table { border-collapse: collapse; width: 100%; }
th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
input { width: 100%; box-sizing: border-box; }
</style>

<script>
function filterTable(input, colIndex) {
    const table = document.getElementById("myTable");
    const tr = table.getElementsByTagName("tr");

    for (let i = 2; i < tr.length; i++) {
        let td = tr[i].getElementsByTagName("td")[colIndex];
        if (td) {
            let txtValue = td.textContent || td.innerText;
            tr[i].style.display = txtValue.toLowerCase().includes(input.value.toLowerCase())
                ? ""
                : "none";
        }
    }
}
</script>

</head>
<body>

<table id="myTable">
<thead>
""");

            finalContent.append(header).append("\n");
            finalContent.append(filterRow).append("\n");
            finalContent.append("</thead>\n<tbody>\n");

        }

        // LOOP ARRAY → banyak row
        for (JsonNode obj : node) {

            if (!obj.isObject()) continue;

            StringBuilder row = new StringBuilder();
            row.append("<tr>");

            for (String key : keys) {
                String value = obj.path(key).asText("");
                row.append("<td>").append(value).append("</td>");
            }

            row.append("</tr>\n");
            finalContent.append(row);
        }

        Files.writeString(
                filePath,
                finalContent.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println("HTML updated: " + filePath.toAbsolutePath());
    }

    public List<String> buildPromptClient2Be(String jsonData) {
        List<String> prompts = new ArrayList<>();
        prompts.add(jsonData);
        return prompts;
    }

    public List<String> buildPromptFe2Be(String jsonData) {
        List<String> prompts = new ArrayList<>();
        prompts.add(jsonData);
        return prompts;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        return text.length() / 3;
    }







}