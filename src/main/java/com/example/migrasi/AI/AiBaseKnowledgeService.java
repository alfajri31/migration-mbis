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

    @Value("${ai.model.data.batch}")
    private int dataBatch;

    @Autowired
    private PromptLoader promptLoader;

    private final RestTemplate restTemplate = new RestTemplate();

    // =========================
    // MAIN ENTRY
    // =========================
    public void processKnowledgeBase(Map<String, List<String>> data, String type) throws Exception {

        int safeContextWindow = contextWindow - 500;

        if (isTokenOverflow(data, safeContextWindow)) {
            log.warn("Can't be proceed: tokens will be overflow");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = mapPhase(data, mapper, safeContextWindow);

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        Map<String, Object> structuredData = buildStructuredData(data, partialSummaries, mapper);

        String finalResult;

        if (dataBatch > 1) {
            finalResult = processBatch(structuredData, mapper, type);
        } else {
            finalResult = processSingle(structuredData, mapper, type);
        }

        log.info("FINAL RESULT:\n{}", finalResult);
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
    private String processBatch(Map<String, Object> structuredData,
                                ObjectMapper mapper,
                                String type) throws Exception {

        Map<String, List<List<?>>> batchedData = splitBatches(structuredData);

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
    private Map<String, List<List<?>>> splitBatches(Map<String, Object> structuredData) {

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
                            "Match `client_columns` to `schema_columns` using STRICT name similarity.\n" +
                            "\n" +
                            "NORMALIZATION:\n" +
                            "- lowercase\n" +
                            "- remove spaces and underscores\n" +
                            "- remove suffix: id, _id\n" +
                            "\n" +
                            "ALLOWED MATCH:\n" +
                            "1. Exact normalized match → 1.0\n" +
                            "2. Minor format variation (username = user_name) → 0.8\n" +
                            "3. Strict synonyms ONLY:\n" +
                            "   name = nama\n" +
                            "   description = keterangan\n" +
                            "   email = email\n" +
                            "   phone = telepon\n" +
                            "   branch = cabang / kanwil\n" +
                            "\n" +
                            "REJECT IF:\n" +
                            "- not clearly similar by name\n" +
                            "- semantic/context guess\n" +
                            "- partial match\n" +
                            "- generic fields (id, name) unless exact\n" +
                            "\n" +
                            "CONFIDENCE:\n" +
                            "- 1.0 = exact\n" +
                            "- 0.8 = variation/synonym\n" +
                            "- ≤0.6 = NO MATCH\n" +
                            "\n" +
                            "OUTPUT:\n" +
                            "{\n" +
                            "  \"discovery\": [\n" +
                            "    {\"client_column\": \"...\", \"schema_column\": \"...\", \"confidence\": 0.0}\n" +
                            "  ],\n" +
                            "  \"not_found\": [\"...\"]\n" +
                            "}\n" +
                            "\n" +
                            "RULE:\n" +
                            "If unsure → NOT MATCH.\n" +
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

        request.put("temperature", 0.0);

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

        ObjectMapper mapper = new ObjectMapper();

        // 🔥 inject color dulu
        content = addColorBasedOnConfidence(content);

        Path filePath;

        if ("client2be".equalsIgnoreCase(type)) {

            // ✅ 1 file saja (no timestamp)
            filePath = directory.resolve("client2be.md");

            JsonNode newData = mapper.readTree(content);
            ArrayNode finalArray;

            if (Files.exists(filePath)) {
                String existing = Files.readString(filePath);

                if (existing.isBlank()) {
                    finalArray = mapper.createArrayNode();
                } else {
                    finalArray = (ArrayNode) mapper.readTree(existing);
                }
            } else {
                finalArray = mapper.createArrayNode();
            }

            // tambah data
            finalArray.add(newData);

            // overwrite biar tetap valid JSON array
            Files.writeString(
                    filePath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalArray),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } else {

            // default pakai timestamp
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            String fileName = type + "_" + timestamp + ".md";
            filePath = directory.resolve(fileName);

            Files.writeString(filePath, content, StandardOpenOption.CREATE);
        }

        System.out.println("File saved: " + filePath.toAbsolutePath());
    }

    private String addColorBasedOnConfidence(String json) {
        try {

            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(json);

            // cek apakah ada "discovery"
            if (root.has("discovery") && root.get("discovery").isArray()) {

                ArrayNode discoveryArray = (ArrayNode) root.get("discovery");

                for (JsonNode item : discoveryArray) {

                    if (item.has("confidence") && item instanceof ObjectNode) {

                        double confidence = item.get("confidence").asDouble();

                        String colored;

                        if (confidence >= 0.7) {
                            colored = "**🟢 " + confidence + "**";
                        } else if (confidence >= 0.5) {
                            colored = "**🟡 " + confidence + "**";
                        } else {
                            colored = "**🔴 " + confidence + "**";
                        }

                        ((ObjectNode) item).put("flag", colored);
                    }
                }
            }

            // return JSON (pretty biar enak dibaca)
            return mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);

        } catch (Exception e) {
            e.printStackTrace(); // penting buat debug
            return json; // fallback kalau error
        }
    }


}