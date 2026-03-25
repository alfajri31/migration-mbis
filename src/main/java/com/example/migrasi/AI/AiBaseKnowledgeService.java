package com.example.migrasi.AI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

    @Value("${agent.host.url.text}")
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

        LinkedHashMap<String, Object> structuredData = buildStructuredData(data, partialSummaries, mapper);

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

    private LinkedHashMap<String, Object> buildStructuredData(Map<String, List<String>> originalData,
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

        LinkedHashMap<String, List<Object>> merged = new LinkedHashMap<>();

        for (Object obj : parsed) {
            Map<String, Object> map = (Map<String, Object>) obj;

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .addAll(list);
                }
            }
        }

        LinkedHashMap<String, Object> structuredData = new LinkedHashMap<>();

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

    private List<LinkedHashMap<String, Object>> transformSchemaDb(List<Object> schemaList) {

        List<LinkedHashMap<String, Object>> result = new ArrayList<>();

        for (Object obj : schemaList) {

            Map<String, Object> tableMap = (Map<String, Object>) obj;

            String tableName = (String) tableMap.get("table");

            Map<String, Object> ddl = (Map<String, Object>) tableMap.get("ddl");
            List<Map<String, Object>> columns =
                    (List<Map<String, Object>>) ddl.get("columns");

            List<String> fields = columns.stream()
                    .map(col -> (String) col.get("name"))
                    .toList();

            List<LinkedHashMap<String, Object>> rows =
                    (List<LinkedHashMap<String, Object>>) tableMap.get("rows");

            LinkedHashMap<String, Object> formatted = new LinkedHashMap<>();
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
    private void processBatch(LinkedHashMap<String, Object> structuredData,
                              ObjectMapper mapper,
                              String type,
                              int autoNumSplitBatch) throws Exception {

        /**
         * guide comment
         * autoNumBatch = 5
         * split batch fe dan schema_db dengan autoNumBatch nya
         * setelah di split:
         * misal total fe (master key) batch ada 10 batch
         * misal total schema batch (ref key) ada 20 batch
         * Iterasi sebanyak jumlah batch fe (10 batch) disilangkan ke sejumlah schema_db batch per autonumbatch
         * F1 → schema 0–4
         * F1 → schema 5–10
         * F1 → schema 10-15
         * F1 → schema 15-20
         * F2 → schema 0–4
         * F2 → schema 5–10
         * F2 → schema 10-15
         * F2 → schema 15-20
         * .......................................
         * F10 → schema 15-20
         */

        List<String> keys = new ArrayList<>(structuredData.keySet());

        String masterKey = keys.get(0);

        String referenceKey = keys.get(1);

        // frontend tetap split
        List<List<?>> masterBatches =
                splitSingle(structuredData.get(masterKey), autoNumSplitBatch);

        List<?> fullRefList = (List<?>) structuredData.get(referenceKey);

        int refSize = fullRefList.size();

        for (int i = 0; i < masterBatches.size(); i++) {

            List<?> masterBatch = masterBatches.get(i);

            // 🔥 ref SELALU mulai dari 0 tiap master
            for (int start = 0; start < refSize; start += autoNumSplitBatch) {

                int end = Math.min(start + autoNumSplitBatch, refSize);

                List<?> referenceBatch = fullRefList.subList(start, end);

                LinkedHashMap<String, Object> batchData = new LinkedHashMap<>();
                batchData.put(masterKey, masterBatch);
                batchData.put(referenceKey, referenceBatch);

                log.info("master key{} → ref key {}-{}",
                        i + 1, start, end - 1);

                callSummary(
                        batchData,
                        mapper.writeValueAsString(batchData),
                        type
                );
            }
        }
    }
    // =========================
    // SPLIT BATCH
    // =========================
    private List<List<?>> splitSingle(Object data, int batchSize) {

        List<?> fullList = (List<?>) data;

        List<List<?>> batches = new ArrayList<>();

        for (int start = 0; start < fullList.size(); start += batchSize) {

            int end = Math.min(start + batchSize, fullList.size());

            batches.add(fullList.subList(start, end));
        }

        return batches;
    }

    // =========================
    // SINGLE PROCESS
    // =========================
    private void processSingle(LinkedHashMap<String, Object> structuredData,
                                 ObjectMapper mapper,
                                 String type) throws Exception {

        callSummary(
                structuredData,
                mapper.writeValueAsString(structuredData),
                type
        );

    }

    private void callSummary(LinkedHashMap<String,Object> dataMap,String dataJson, String type) {

        ObjectMapper mapper  = new ObjectMapper();

        String systemPrompt;

        if(type.equals("fe2be")) {

            systemPrompt = """
                        RESPONSE HARUS JSON SAJA! TANPA TAMBAHAN PENJELASAN TEKS TAMBAHAN LAGI.
                        
                        SUMBER DATA:
                        frontend sebagai master key
                        schema_db sebagai reference key
                            
                        PERAN WAJIB:
                        1. AI mencari kemiripan column dari master key ke reference key yang sama atau paling mirip.
                        2. AI mencari field value yang kosong atau null saja dari master key dan reference key.
                        3. AI mencari nama column yang sama di reference key yaitu schema_db.schema_columns nya.
                        4. AI cek apakah value column master key di schema_db.sample_data nya itu kosong atau null berdasarkan nama kolom di schema_db.schema_columns
                        5. AI mapping hasil discoverynya ke dalam format output json
                        6. AI cek jika nama column sama dan nilai kosong atau null maka field_is_empty true jika tidak kosong atau tidak null maka field_is_empty false
                       
                        
                        FORMAT OUTPUT:
                        [
                            {
                              "master_field_empty": "...",
                              "existing_ref_table_name": "...",
                              "existing_ref_table_field_name": "...",
                              "existing_ref_field_empty": "true | false",
                              "reason": "..."
                            }
                        ]
                        
                        RULE:
                        - Hanya JSON
                        """;

            List<String> keys = new ArrayList<>(dataMap.keySet());
            String masterKey = keys.get(0);
            log.info("request master key {}", dataMap.get(masterKey));
            List<String> userPrompts = buildPromptFe2Be(dataJson);


            for (String prompt : userPrompts) {

                LinkedHashMap<String, Object> request = new LinkedHashMap<>();

//                List<Map<String, Object>> messages = new ArrayList<>();
//
//                messages.add(Map.of("role", "system", "content", systemPrompt));
//
//                messages.add(Map.of("role", "user", "content", prompt));

                String finalPrompt =
                        "System:\n" + systemPrompt + "\n\n" +
                                "User:\n" + prompt;

                request.put("model", aiModel);

//                request.put("messages", messages);

                request.put("prompt",finalPrompt);

                request.put("keep_alive",0);

                request.put("stream", false);

                Map<String, Object> options = new HashMap<>();

                options.put("temperature", 0);

                request.put("options", options);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

//                Map body = response.getBody();
//
//                Map message = (Map) body.get("message");
//
//                String content = extractJson(message.get("content").toString());

                try {
//                    JsonNode node = mapper.readTree(content);

                    String content = extractJson(Objects.requireNonNull(response.getBody()).get("response").toString());

                    JsonNode node = mapper.readTree(content);

                    saveToFile(node, type);

                } catch (Exception e) {
                    log.info("error json node {}", e.getMessage());
                }
            }
        }

        if(type.equals("client2be")) {

            systemPrompt = """
                    OUTPUT HARUS BERUPA JSON SAJA!. TANPA PENJELASAN TEKS TAMBAHAN LAGI.
                        
                    SUMBER DATA:
                    client_data sebagai pusat atau master key set
                    schema_db sebagai reference key set
                    
                    PERAN WAJIB:
                    1. AI mencari kemiripan column dari master key ke reference key yang sama atau paling mirip.
                    2. AI mencari nama column yang sama atau paling mirip di reference key yaitu do schema_db.schema_columns nya.
                    3. AI mengecek apakah value column di schema_db.sample_data nya itu kosong atau null berdasarkan nama kolom master key di schema_db.schema_columns
                    4. AI mengecek jika kosong atau null maka field_is_empty true jika tidak kosong atau tidak null maka field_is_empty false
                    5. AI mapping hasil discoverynya kedalam format output json
                    6. AI memberikan hasil tingkat kemiripan similarity confidence berdasarkan kemiripan column di master key dan reference key            
                                                               
                                        
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
                            "master_column_name": "...",
                            "match_found": true | false,
                            "ref_table_name": "schema_db.table",
                            "ref_column_name": "...",
                            "json_path_in_ref": "schema_db.schema_columns",
                            "similarity_confidence": "high | medium | low",
                            "reason_confidence": ""
                        }
                    ]\s
                    """;

            List<String> userPrompts = buildPromptClient2Be(dataJson);

            for (String prompt : userPrompts) {

                LinkedHashMap<String, Object> request = new LinkedHashMap<>();

                List<Map<String, Object>> messages = new ArrayList<>();

//                messages.add(Map.of("role", "system", "content", systemPrompt));
//
//                messages.add(Map.of("role", "user", "content", prompt));

                String finalPrompt =
                        "System:\n" + systemPrompt + "\n\n" +
                                "User:\n" + prompt;

                request.put("model", aiModel);

//                request.put("messages", messages);

                request.put("prompt",finalPrompt);

                Map<String, Object> options = new HashMap<>();

                options.put("temperature", 0);

                request.put("options", options);

                request.put("stream", false);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

//                Map body = response.getBody();

//                Map message = (Map) body.get("message");

//                String content = extractJson(message.get("content").toString());

                try {

                    String content = extractJson(Objects.requireNonNull(response.getBody()).get("response").toString());

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

    public String extractJson(String raw) {
        if (raw == null) return "";

        int start = raw.indexOf("[");
        int end = raw.lastIndexOf("]");

        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1);
        }

        return raw;
    }







}