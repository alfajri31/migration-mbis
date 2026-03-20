package com.example.migrasi.AI;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiBaseKnowledgeService {

    @Value("${agent.host.url}")
    private String url;

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    @Value("${ai.model.context.base.knowledge}")
    private int contextWindow;
    @Value("${ai.context.max.prompt.words}")
    private int maxWords;

    private final String reduceUserPrompt="Berikan summary perbaikannya secara keseluruhan. Data: ";

    private final RestTemplate restTemplate = new RestTemplate();

    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

        int safeContextWindow = contextWindow - 500;

        int batchSize = 1000;

        Map<String, List<String>> chunkedData = chunkedBySize(data, batchSize);

        int totalChunkCounts = 0;

        for (List<String> list : chunkedData.values()) {
            totalChunkCounts += list.size();
        }

        int estimationSummaryTokens = totalChunkCounts * (maxWords * 3);

        if (estimationSummaryTokens >= safeContextWindow) {
            log.warn("Skip reduce: estimasi token will be overflow");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = new ArrayList<>();

        log.info("START MAP PHASE (per chunk processing)");

        int index=0;

        int subIndex=0;

        for (Map.Entry<String, List<String>> entry : chunkedData.entrySet()) {

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

                    subIndex=0;

                    log.warn("Chunk over context window, splitting... tokens={}", estimatedTokens);

                    List<String> safeChunks = splitByTokenSafe(jsonChunk);

                    for (String safeChunk : safeChunks) {

                        String prompt = buildUserPrompt() + ": " + safeChunk;

                        String result = callAI(key, prompt, index, safeChunks.size());

                        partialSummaries.add(result);

                        subIndex++;

                        log.info("response complete chunk at index - {} sub-index {} of safe chunks {}", index,subIndex,safeChunks.size()-1);

                    }

                } else {

                    String prompt = buildUserPrompt()+ ": " + jsonChunk;

                    String result = callAI(key, prompt, index, chunks.size());

                    partialSummaries.add(result);
                }

                log.info("response complete chunk at index - {} sub-index {} of {}", index,subIndex,totalChunkCounts);

                index++;
            }
        }

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        mapper = new ObjectMapper();

        String combinedSummary = mapper.writeValueAsString(partialSummaries);

        String reducePrompt = reduceUserPrompt + combinedSummary;

        String finalResult = callAISummary(reducePrompt,index, chunkedData.size());

        log.info("FINAL RESULT:\n{}", finalResult);

        saveToFile(finalResult);
    }

    private String callAI(String key,String userPrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", key+": "+buildSystemPrompt()
        ));

        messages.add(Map.of(
                "role", "user",
                "content", userPrompt
        ));

        Map<String, Object> request = new HashMap<>();

        request.put("options", Map.of(
                "temperature", 0
        ));

        request.put("model", aiModel);
        request.put("messages", messages);
        request.put("stream", false);

        try {

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            Map body = response.getBody();

            Map message = (Map) body.get("message");

            return message.get("content").toString();

        } catch (Exception e) {
            log.error("Error callAI: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String callAISummary(String userPrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "Ringkas teks menjadi singkat, utuh, dan jelas. Ambil poin utama saja."
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

    public Map<String, List<String>> chunkedBySize(Map<String, List<String>> data, int maxLength)
            throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();

            List<String> list = entry.getValue();

            List<String> chunks = new ArrayList<>();

            StringBuilder current = new StringBuilder();

            for (String item : list) {

                String minified = mapper.writeValueAsString(
                        mapper.readTree(item)
                );

                // kalau item sendiri lebih besar
                if (minified.length() > maxLength) {

                    if (!current.isEmpty()) {

                        chunks.add(current.toString());

                        current = new StringBuilder();
                    }

                    chunks.add(minified);

                    continue;
                }

                if (current.length() + minified.length() + 1 > maxLength) {
                    if (!current.isEmpty()) {
                        chunks.add(current.toString());
                        current = new StringBuilder();
                    }
                }

                if (!current.isEmpty()) {
                    current.append(",");
                }

                current.append(minified);
            }

            if (!current.isEmpty()) {
                chunks.add(current.toString());
            }

            result.put(key, chunks);
        }

        return result;
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

    private List<String> splitByTokenSafe(String text) {

        List<String> results = new ArrayList<>();

        int estimatedTokens = estimateTokens(text);

        if (estimatedTokens <= contextWindow) {
            results.add(text);
            return results;
        }

        // split jadi 2 (bisa juga 3 atau dynamic)
        int mid = text.length() / 2;

        String part1 = text.substring(0, mid);
        String part2 = text.substring(mid);

        results.addAll(splitByTokenSafe(part1));
        results.addAll(splitByTokenSafe(part2));

        return results;
    }

    private String buildUserPrompt() {
        return "Berikan penjelasan singkat dari potongan data berikut (maks:"
                + maxWords + " kata). Data ";
    }

    private String buildSystemPrompt() {
        return "Jawaban WAJIB maksimal "
                + maxWords + " kata. Jika lebih, ringkas ulang. Jangan tampilkan reasoning.";
    }

}