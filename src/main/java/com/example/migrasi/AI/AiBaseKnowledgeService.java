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

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    @Value("${prompt.user.base.knowledge}")
    private String userPrompt;

    @Value("${prompt.user.base.knowledge.reduce}")
    private String reduceUserPrompt;


    @Value("${system.feedback.base.knowledge}")
    private String systemFeedback;

    @Value("${agent.host.url}")
    private String url;



    private final RestTemplate restTemplate = new RestTemplate();

    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

        // chunk by character string
        Map<String, List<String>> chunkedData = chunkByLengthSize(data, 2000);

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = new ArrayList<>();

        log.info("START MAP PHASE (per chunk processing)");

        int index=0;

        for (Map.Entry<String, List<String>> entry : chunkedData.entrySet()) {

            String key = entry.getKey();
            List<String> chunks = entry.getValue();

            log.info("total all size chunks {} ",chunks.size());

            for (String chunk : chunks) {

                String jsonChunk = mapper.writeValueAsString(
                        Map.of(key, mapper.readValue("[" + chunk + "]", List.class))
                );

                int estimatedTokens = estimateTokens(jsonChunk);

                log.info("Chunk [{}] tokens: {}", key, estimatedTokens);

                String prompt = userPrompt + ": " + jsonChunk;

                String result = callAI(key,prompt,index,chunks.size());

                partialSummaries.add(result);

                index+=1;
            }
        }

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        mapper = new ObjectMapper();

        String combinedSummary = mapper.writeValueAsString(partialSummaries);

        String reducePrompt = reduceUserPrompt + combinedSummary;

        String finalResult = callAISummary("summary",reducePrompt,index, chunkedData.size());

        log.info("FINAL RESULT:\n{}", finalResult);

        saveToFile(finalResult);
    }

    private String callAI(String key,String userPrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "pembahasan seputar "+key+", "+systemFeedback
        ));

        messages.add(Map.of(
                "role", "user",
                "content", userPrompt
        ));

        Map<String, Object> request = new HashMap<>();

        request.put("options", Map.of(
                //num_predict -> set maximum character token in first place request
                "num_predict", 200,
                //set to 0 after request no memory
                "keep_alive", "0"
        ));

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

    private String callAISummary(String key,String userPrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "pembahasan seputar kekurangannya "+key
        ));

        messages.add(Map.of(
                "role", "user",
                "content", userPrompt
        ));

        Map<String, Object> request = new HashMap<>();

        request.put("options", Map.of(
                //set to 0 after request no memory
                "keep_alive", "0"
        ));

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

    public Map<String, List<String>> chunkByLengthSize(Map<String, List<String>> data, int maxLength)
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

        return text.length() / 4;
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


        private String extractJson(String text) {

            String json="";

            try {
                if (text == null || text.isEmpty()) {
                    return null;
                }

                int start = text.indexOf("[");
                int end = text.lastIndexOf("]");

                if (start == -1 || end == -1 || end <= start) {
                    return null;
                }

                json = text.substring(start, end + 1);

                // normalize whitespace
                json = json.replaceAll("[\\r\\n\\t]", " ").trim();

                // remove trailing comma
                json = json.replaceAll(",\\s*]", "]");
            }
            catch (Exception e) {
                log.info("error parser occurred {}", e.getMessage());
            }

            return json;
        }

}