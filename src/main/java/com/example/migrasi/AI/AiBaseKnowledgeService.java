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

    public void processKnowledgeBase(Map<String, List<String>> data,String type) throws Exception {

        int safeContextWindow = contextWindow - 500;

        int totalChunkCounts = 0;

        int totalLength = 0;

        for (List<String> list : data.values()) {

            if(list.toArray().length < safeContextWindow) {

                totalLength+= list.toArray().length;

            }
            totalChunkCounts += list.size();
        }

        int estimationSummaryTokens = totalChunkCounts * (totalLength * 3);

        if (estimationSummaryTokens >= safeContextWindow) {

            log.warn("Can't be proceed: tokens will be overflow");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = new ArrayList<>();

        log.info("START MAP PHASE (per chunk processing)");

        int index=0;

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();

            List<String> chunks = entry.getValue();

            log.info("total all size chunks: {} ",chunks.size());

            for (String chunk : chunks) {

                String jsonChunk = mapper.writeValueAsString(
                        Map.of(key, mapper.readValue("[" + chunk + "]", List.class))
                );

                int estimatedTokens = estimateTokens(jsonChunk);

                log.warn("Chunk tokens={}", estimatedTokens);

                if (estimatedTokens > safeContextWindow) {

                    log.warn("Chunk over context window, splitting... tokens={} SKIP! I assume this only data insert remnants", estimatedTokens);

                    continue;

                } else {
                    partialSummaries.add(jsonChunk);
                }
                index++;
            }
        }

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        mapper = new ObjectMapper();

        ObjectMapper finalMapper = mapper;

        List<Object> parsed = partialSummaries.stream()
                .map(s -> {
                    try {

                        return finalMapper.readValue(s, Object.class);

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }
                })
                .toList();

        //grouped by
        Map<String, List<Object>> merged = new HashMap<>();

        for (Object obj : parsed) {

            Map<String, Object> map = (Map<String, Object>) obj;

            for (Map.Entry<String, Object> entry : map.entrySet()) {

                if (entry.getValue() instanceof List) {

                    merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())

                            .addAll((List<Object>) entry.getValue());

                }
            }
        }

        // OPTIONAL: jaga urutan penting
        Map<String, Object> structuredData = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            structuredData.put(
                    entry.getKey(),
                    merged.getOrDefault(entry.getKey(), new ArrayList<>())
            );
        }

        StringBuilder finalResult = new StringBuilder();

        if (dataBatch > 1) {

            // =========================
            // STEP 1: SPLIT PER KEY
            // =========================
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

            // =========================
            // STEP 2: CROSS COMBINATION
            // =========================
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

                    Map<String, Object> batchData = new LinkedHashMap<>();

                    batchData.put(key1, key1Batches.get(i));

                    batchData.put(key2, key2Batches.get(j));

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

        } else {

            finalResult = new StringBuilder(callSummary(
                    mapper.writeValueAsString(structuredData),
                    index,
                    ((List<?>) structuredData.values().iterator().next()).size(),
                    type
            ));

            saveToFile(finalResult.toString(), type);
        }

        log.info("FINAL RESULT:\n{}", finalResult);
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
                            "Match each schema_column to the most similar client_field.\n" +
                            "\n" +
                            "MATCHING RULES:\n" +
                            "- Use column name similarity (string + semantic allowed)\n" +
                            "- You MAY normalize text (lowercase, remove underscore, etc.)\n" +
                            "- You MAY use common synonyms (name=nama, phone=telepon, branch=cabang, etc.)\n" +
                            "- Do NOT use sample data\n" +
                            "\n" +
                            "CONFIDENCE SCORING (IMPORTANT):\n" +
                            "- 1.0 → exact match or very obvious (same word / minor variation)\n" +
                            "- 0.8 → very similar meaning (clear synonym or translation)\n" +
                            "- 0.6 → somewhat similar (related but not exact)\n" +
                            "- 0.3 → weak similarity\n" +
                            "- 0.0 → no reasonable similarity\n" +
                            "\n" +
                            "OUTPUT RULES:\n" +
                            "- If best match exists → put in discovery with confidence score\n" +
                            "- If confidence <= 0.3 → put in not_found instead\n" +
                            "- NEVER force match if not reasonable\n" +
                            "\n" +
                            "OUTPUT FORMAT:\n" +
                            "{\n" +
                            "  \"discovery\": [\n" +
                            "    {\n" +
                            "      \"schema_column\": \"...\",\n" +
                            "      \"matched_column\": \"...\",\n" +
                            "      \"confidence\": 0.0\n" +
                            "    }\n" +
                            "  ],\n" +
                            "  \"not_found\": [\"...\"]\n" +
                            "}\n" +
                            "\n" +
                            "CONSTRAINTS:\n" +
                            "- Every schema_column MUST appear exactly once\n" +
                            "- No duplicates\n" +
                            "- No missing fields\n" +
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