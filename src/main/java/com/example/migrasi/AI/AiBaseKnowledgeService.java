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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        Map<String, Object> structuredData = buildStructuredData(data, partialSummaries, mapper);

        String finalResult;

        int autoBatch = calculateBatchCountSmart(structuredData);

        if (autoBatch > 1) {
            finalResult = processBatch(structuredData, mapper, type,autoBatch);
        } else {
            finalResult = processSingle(structuredData, mapper, type);
        }

        log.info("FINAL RESULT:\n{}", finalResult);
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
//            if ("schema_db".equals(key)) {
//                structuredData.put(key, transformSchemaDb(data));
//            } else {
//                structuredData.put(key, data);
//            }
            structuredData.put(key, data);
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
    private String processBatch(Map<String, Object> structuredData,
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

                String result = callSummary(
                        mapper.writeValueAsString(batchData),
                        0,
                        0,
                        type
                );

                saveToFile(result, type);

                finalResult.append(result).append("\n");
            }
        }

        return finalResult.toString();
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
    private String processSingle(Map<String, Object> structuredData,
                                 ObjectMapper mapper,
                                 String type) throws Exception {

        int size = ((List<?>) structuredData.values().iterator().next()).size();

        String result = callSummary(
                mapper.writeValueAsString(structuredData),
                size,
                size,
                type
        );

        saveToFile(result, type);

        return result;
    }

    private String callSummary(String dataJson,int chunkIndex,int totalChunkSize,String type) {

        String systemPrompt="";

        String userPrompt="";

        List<Map<String, Object>> messages = new ArrayList<>();

        if(type.equals("fe2be")) {

            systemPrompt ="" +
                    "Kamu adalah AI yang bertugas melakukan validasi antara key fields frontend (FE) dan key schema_database. " +
                    "Tugasmu adalah menemukan fields yang tidak konsisten, " +
                    "seperti fields yang tidak ada di schema_db seperti perbedaan nama, " +
                    "atau tipe data yang tidak sesuai,dan lain lain!";


            userPrompt =
                    "\"Bandingkan fields dari key 'frontend' dengan key 'schema_db', " +
                            "lalu tampilkan field yang tidak " +
                            "konsisten antar " +
                            "data pada key " +
                            "(tidak ada di schema_db, typo, atau mismatch penamaan di schema_db, dan lain lain)." +
                            "\"\n\n"+dataJson;
        }

        if(type.equals("client2be")) {

            systemPrompt =
                    "You are a strict JSON generator.\n" +
                            "\n" +
                            "You must output ONLY valid JSON.\n" +
                            "No explanation.\n" +
                            "No text outside JSON.\n" +
                            "\n" +
                            "If output is invalid JSON, you have failed.";

            userPrompt =
                    "ONLY OUTPUT JSON.\n" +
                            "\n" +
                            "TASK:\n" +
                            "Match client_columns to schema_db columns using name and sample_data.\n" +
                            "\n" +
                            "CONTEXT:\n" +
                            "- This is partial (batched) data\n" +
                            "- Use best guess if needed\n" +
                            "\n" +
                            "IMPORTANT:\n" +
                            "- schema_value MUST be actual row data\n" +
                            "- DO NOT use column name or data type (e.g. 'uuid', 'string', 'int') as value\n" +
                            "- If only type is available → treat as no value\n" +
                            "\n" +
                            "RULES:\n" +
                            "- normalize: lowercase, remove spaces, underscores, 'id'\n" +
                            "- if no value, rely on name similarity only\n" +
                            "\n" +
                            "CONFIDENCE:\n" +
                            "- exact name + valid value match → high\n" +
                            "- partial name or no value → medium\n" +
                            "- unclear → low\n" +
                            "\n" +
                            "OUTPUT:\n" +
                            "{\n" +
                            "  \"found\": [\n" +
                            "    {\n" +
                            "      \"client_column\": \"...\",\n" +
                            "      \"schema_column\": \"...\",\n" +
                            "      \"confidence\": \"high\",\n" +
                            "      \"client_value\": \"...\",\n" +
                            "      \"schema_value\": \"...\",\n" +
                            "      \"reasoning\": \"...\"\n" +
                            "    }\n" +
                            "  ],\n" +
                            "  \"not_found\": [\n" +
                            "    {\n" +
                            "      \"client_column\": \"...\",\n" +
                            "      \"reason\": \"low confidence or no valid value\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n" +
                            "\n" +
                            "RULE:\n" +
                            "- ONLY high goes to found\n" +
                            "\n" +
                            "REASONING:\n" +
                            "- mention name match\n" +
                            "- mention if value is valid or missing\n" +
                            "\n" +
                            "DATA:\n" +
                            dataJson;
        }


        messages.add(Map.of(
                "role", "system",
                "content",systemPrompt
        ));
        messages.add(Map.of(
                "role", "user",
                "content", userPrompt
        ));

        Map<String, Object> request = new HashMap<>();

        request.put("model", aiModel);

        request.put("messages", messages);

        request.put("stream", false);

        try {
            log.info(request.get("messages").toString());

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            Map body = response.getBody();

            Map message = (Map) body.get("message");

            log.info("response complete chunk at index - {} of {}", chunkIndex,totalChunkSize);

            return message.get("content").toString();

        } catch (Exception e) {
            log.error("Error callAI: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
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


}