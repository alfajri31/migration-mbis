package com.example.migrasi.AI;

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

    @Value("${prompt.user.base.knowledge}")
    private String promptUser;

    @Value("${system.feedback.base.knowledge}")
    private String systemFeedback;

    private final RestTemplate restTemplate = new RestTemplate();


    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

        String url = "http://localhost:11434/api/chat";

        int globalIndex = 0;

        // 🔹 step 1: chunk per key
        Map<String, List<String>> chunkedData =
                chunkByLengthSize(data, 500);

        for (Map.Entry<String, List<String>> entry : chunkedData.entrySet()) {

            String key = entry.getKey();

            List<String> chunks = entry.getValue();

            log.info("==== PROCESSING KEY: {} | total chunks: {} ====", key, chunks.size());

            int index = 0;

            for (String chunk : chunks) {

                index++;
                globalIndex++;

                log.info("---- [{}] CHUNK {}/{} ----", key, index, chunks.size());

                // ✅ Bungkus per key (INI YANG PENTING)
                String finalPrompt = promptUser +
                        "\n\nBase Knowledge (" + key + "):\n" +
                        "{ \"" + key + "\": [\n" + chunk + "\n] }\n---";

                List<Map<String, Object>> messages = new ArrayList<>();

                messages.add(Map.of(
                        "role", "system",
                        "content", systemFeedback
                ));

                messages.add(Map.of(
                        "role", "user",
                        "content", finalPrompt
                ));

                Map<String, Object> options = new HashMap<>();
                options.put("temperature", 0);
                options.put("num_predict", 100);

                Map<String, Object> request = new HashMap<>();
                request.put("options", options);
                request.put("model", aiModel);
                request.put("messages", messages);
                request.put("stream", false);

                try {

                    long startTime = System.currentTimeMillis();

                    log.info("Calling AI API [{} - chunk {}]", key, index);

                    ResponseEntity<Map> response =
                            restTemplate.postForEntity(url, request, Map.class);

                    long duration = System.currentTimeMillis() - startTime;

                    log.info("AI response [{} - chunk {}] ({} ms)", key, index, duration);

                    Map body = response.getBody();
                    Map message = (Map) body.get("message");

                    String content = message.get("content").toString();

                    log.info("AI RESPONSE [{} - chunk {}]:\n{}", key, index, content);

                } catch (Exception e) {
                    log.error("Error Occurred prompt AI [{} - chunk {}]: {}", key, index, e.getMessage());
                    throw new Exception(e.getMessage());
                }
            }
        }
    }

    public Map<String, List<String>> chunkByLengthSize(Map<String, List<String>> data, int maxLength) {

        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();
            List<String> list = entry.getValue();

            List<String> chunks = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String item : list) {

                // 🔹 kalau item sendiri lebih besar dari maxLength
                if (item.length() > maxLength) {

                    // simpan current dulu kalau ada isi
                    if (!current.isEmpty()) {
                        chunks.add(current.toString());
                        current = new StringBuilder();
                    }

                    // item jadi chunk sendiri
                    chunks.add(item);
                    continue;
                }

                // 🔹 kalau ditambah melebihi limit → simpan dulu
                if (current.length() + item.length() > maxLength) {
                    if (!current.isEmpty()) {
                        chunks.add(current.toString());
                        current = new StringBuilder();
                    }
                }

                current.append(item).append("\n");
            }

            // 🔹 sisa terakhir
            if (!current.isEmpty()) {
                chunks.add(current.toString());
            }

            result.put(key, chunks);
        }

        return result;
    }
}