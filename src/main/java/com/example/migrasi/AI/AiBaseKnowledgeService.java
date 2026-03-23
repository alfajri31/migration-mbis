package com.example.migrasi.AI;

import com.example.migrasi.prompt.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    @Autowired
    private PromptLoader promptLoader;

    private final static int reservedTokens= 5000;
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

        StringBuilder finalResult = new StringBuilder();

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

    private void callSummary(String dataJson, String type) throws Exception {

        String systemPrompt="";

        String userPrompt="";

        if(type.equals("fe2be")) {

            Map<String, Object> request = new HashMap<>();

            List<Map<String, Object>> messages = new ArrayList<>();

            systemPrompt ="" +
                    "Kamu adalah AI yang bertugas melakukan validasi antara key fields frontend (FE) dan key schema_database. " +
                    "Tugasmu adalah menemukan fields yang tidak konsisten, " +
                    "seperti fields yang tidak ada di schema_db seperti perbedaan nama, " +
                    "atau tipe data yang tidak sesuai,dan lain lain!";


            userPrompt =
                    "\"Bandingkan fields dari key klien 'client' dengan key db 'schema_db', " +
                            "lalu tampilkan field yang tidak " +
                            "konsisten antar " +
                            "data pada key " +
                            "(tidak ada di schema_db, typo, atau mismatch penamaan di schema_db, dan lain lain)." +
                            "\"\n\n"+dataJson;

            messages.add(Map.of(
                    "role", "system",
                    "content",systemPrompt
            ));
            messages.add(Map.of(
                    "role", "user",
                    "content", userPrompt
            ));

            request.put("model", aiModel);

            request.put("messages", messages);

            request.put("stream", false);
        }

        if(type.equals("client2be")) {

            List<String> prompts = buildPromptList(dataJson);

            systemPrompt =
                    "Kamu adalah AI yang bertugas melakukan mapping antara field dari client dengan field dari database schema.\n" +
                            "\n" +
                            "Tugas kamu:\n" +
                            "- Menilai tingkat kemiripan (confidence) antara dua field: field A (client) dan field B (schema).\n" +
                            "- Berikan nilai confidence dari 0 sampai 1:\n" +
                            "  - 1 = sangat yakin (makna sama persis)\n" +
                            "  - 0.7 - 0.9 = sangat mirip (hampir sama)\n" +
                            "  - 0.4 - 0.6 = cukup relevan\n" +
                            "  - 0.1 - 0.3 = sedikit berkaitan\n" +
                            "  - 0 = tidak ada hubungan\n" +
                            "\n" +
                            "Aturan penting:\n" +
                            "- Gunakan pemahaman SEMANTIK (arti kata), bukan hanya kesamaan teks.\n" +
                            "- Pertimbangkan konteks umum database (contoh: Email → email, Username → user_name, Password → password_hash).\n" +
                            "- Field \"id\" biasanya adalah primary key, cocok dengan field yang berfungsi sebagai identifier.\n" +
                            "- Abaikan perbedaan huruf besar/kecil dan simbol.\n" +
                            "- Jika field client jelas tidak berhubungan dengan schema, beri nilai rendah (0 atau mendekati 0).\n" +
                            "\n" +
                            "Output WAJIB dalam format JSON TANPA penjelasan tambahan:\n" +
                            "{\n" +
                            "  \"client_field\": \"<nama field client>\",\n" +
                            "  \"schema_field\": \"<nama field schema>\",\n" +
                            "  \"confidence\": <angka 0 - 1>\n" +
                            "}";

            for (int i = 0; i < prompts.size(); i++) {

                Map<String, Object> request = new HashMap<>();

                List<Map<String, Object>> messages = new ArrayList<>();

                messages.add(Map.of(
                        "role", "system",
                        "content", systemPrompt
                ));

                messages.add(Map.of(
                        "role", "user",
                        "content", prompts.get(i)
                ));

                request.put("model", aiModel);
                request.put("messages", messages);
                request.put("stream", false);

                log.info("Request prompt: {}", prompts.get(i));

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

                Map body = response.getBody();

                Map message = (Map) body.get("message");

                String content = message.get("content").toString();

                saveToFile(content, type);

                log.info("response complete index {} of {}", i + 1, prompts.size());
            }
        }
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        return text.length() / 3;
    }

    private void saveToFile(String content, String type) throws Exception {

        String folderPath = "summary";
        Path directory = Paths.get(folderPath);

        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        // inject color dulu
        try {
            content = addColorBasedOnConfidence(content);

        }catch (Exception e) {
            e.printStackTrace();

        }
        // 🔥 1 file per type (tanpa timestamp)
        String fileName = type.toLowerCase() + ".md";
        Path filePath = directory.resolve(fileName);

        // 🔥 separator antar result
        String separator = "\n\n--- NEW RESULT ---\n\n";

        String finalContent;

        if (Files.exists(filePath)) {
            // append dengan separator
            finalContent = separator + content;
        } else {
            // file baru tanpa separator di awal
            finalContent = content;
        }

        Files.writeString(
                filePath,
                finalContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println("File appended: " + filePath.toAbsolutePath());
    }

    private String addColorBasedOnConfidence(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(json);

            // ===== HANDLE FOUND =====
            if (root.has("found") && root.get("found").isArray()) {
                ArrayNode foundArray = (ArrayNode) root.get("found");

                for (JsonNode item : foundArray) {
                    if (item instanceof ObjectNode) {
                        String confidence = item.has("confidence")
                                ? item.get("confidence").asText()
                                : "high";

                        String colored = "**🟢 " + confidence + "**";
                        ((ObjectNode) item).put("flag", colored);
                    }
                }
            }

            // ===== HANDLE NOT_FOUND =====
            if (root.has("not_found") && root.get("not_found").isArray()) {
                ArrayNode notFoundArray = (ArrayNode) root.get("not_found");

                for (JsonNode item : notFoundArray) {
                    if (item instanceof ObjectNode) {
                        ((ObjectNode) item).put("flag", "**🔴 not_found**");
                    }
                }
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        } catch (Exception e) {
            e.printStackTrace();
            return json;
        }
    }

    public List<String> buildPromptList(String jsonData) {
        List<String> prompts = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonData);

            JsonNode clientData = root.path("client_data");
            JsonNode schemaDb = root.path("schema_db");

            // Loop semua client_data
            for (JsonNode client : clientData) {
                JsonNode clientColumns = client.path("client_columns");

                // Loop semua schema_db
                for (JsonNode schema : schemaDb) {
                    JsonNode schemaColumns = schema.path("schema_columns");

                    // Loop schema columns
                    for (JsonNode schemaCol : schemaColumns) {
                        String schemaColumn = schemaCol.asText();

                        // Loop client columns
                        for (JsonNode clientCol : clientColumns) {
                            String clientColumn = clientCol.asText();

                            // Build prompt per item
                            String prompt = "berapa tingkat konfident dari field "
                                    + clientColumn
                                    + " dengan field "
                                    + schemaColumn
                                    + " dari 0 - 1?";

                            prompts.add(prompt);
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON", e);
        }

        return prompts;
    }


}