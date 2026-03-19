package com.example.migrasi.AI;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiBaseKnowledgeService {

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    @Value("${prompt.user.base.knowledge.final}")
    private String finalUserPrompt;

    @Value("${system.feedback.base.knowledge}")
    private String systemFeedback;

    private final RestTemplate restTemplate = new RestTemplate();

    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

        String url = "http://localhost:11434/api/chat";

//        Map<String, List<String>> chunkedData =
//                chunkByLengthSize(data, 100);

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> finalContext = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();
            List<String> chunks = entry.getValue();

            List<Object> mergedList = new ArrayList<>();

            for (String chunk : chunks) {

                List parsed = mapper.readValue("[" + chunk + "]", List.class);

                mergedList.addAll(parsed);
            }

            finalContext.put(key, mergedList);
        }

        String finalJson = mapper.writeValueAsString(finalContext);

        int estimatedTokens = estimateTokens(finalJson);

        log.info("Estimated tokens: {}", estimatedTokens);

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", systemFeedback
        ));

        String finalPrompt = finalUserPrompt+": "+ finalJson;

        messages.add(Map.of(
                "role", "user",
                "content", finalPrompt
        ));

        Map<String, Object> request = new HashMap<>();
        request.put("model", aiModel);
        request.put("messages", messages);
        request.put("stream", false);

        try {

            long startTime = System.currentTimeMillis();

            log.info("Calling AI API (SINGLE CONTEXT)");

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            long duration = System.currentTimeMillis() - startTime;

            log.info("AI response received ({} ms)", duration);

            Map body = response.getBody();

            Map message = (Map) body.get("message");

            String content = message.get("content").toString();

            log.info("FINAL AI RESPONSE:\n{}", content);

        } catch (Exception e) {
            log.error("Error Occurred prompt AI {}", e.getMessage());
            throw new Exception(e.getMessage());
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

            result.put(key, chunks.subList(0,5));
        }

        return result;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        return text.length() / 4;
    }
}