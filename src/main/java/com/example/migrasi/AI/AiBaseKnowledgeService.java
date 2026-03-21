package com.example.migrasi.AI;

import com.example.migrasi.prompt.PromptLoader;
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

    private final RestTemplate restTemplate = new RestTemplate();

    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

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


        Map<String, Object> prompt = promptLoader.loadPrompt("summary-prompt.json");

        Map<String, Object> userPrompt = (Map<String, Object>) prompt.get("user_prompt");

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

        for(Map.Entry<String, List<String>> entry : data.entrySet()) {

            structuredData.put(entry.getKey(), merged.getOrDefault(entry.getKey(), new ArrayList<>()));

        }

        userPrompt.put("data", structuredData);

        String reducePrompt = mapper.writeValueAsString(userPrompt);

        String finalResult = callSummary(reducePrompt,index, data.size());

        log.info("FINAL RESULT:\n{}", finalResult);

        saveToFile(finalResult);
    }

    private String callSummary(String reducePrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> promptMap = promptLoader.loadPrompt("summary-prompt.json");

        Map<String, Object> systemMap = (Map<String, Object>) promptMap.get("system");

        String content = (String) systemMap.get("content");

        messages.add(Map.of(
                "role", "system",
                "content",content
        ));
        messages.add(Map.of(
                "role", "user",
                "content", reducePrompt
        ));

        Map<String, Object> request = new HashMap<>();

        request.put("model", aiModel);

        request.put("messages", messages);

        request.put("stream", false);

        request.put("top_p", 1);

        request.put("temperature", 0);

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

    private void saveToFile(String content) throws Exception {

        String folderPath = "summary";

        // format tanggal waktu: 2026-03-19_14-30-25
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        String fileName = timestamp + ".md";

        Path directory = Paths.get(folderPath);
        Path filePath = directory.resolve(fileName);

        // buat folder kalau belum ada
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        // simpan file
        Files.writeString(filePath, content, StandardOpenOption.CREATE);

        System.out.println("File saved: " + filePath.toAbsolutePath());
    }


}