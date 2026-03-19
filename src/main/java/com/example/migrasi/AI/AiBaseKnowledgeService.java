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


    public void processKnowledgeBase(List<String> baseKnowledges) throws Exception {

        String url = "http://localhost:11434/api/chat";

        List<String> chunks = chunkByLengthSize(baseKnowledges, 100).subList(0,2);

        log.info("Total chunks created: {}", chunks.size());

        int index = 0;

        for (String chunk : chunks) {

            index++;

            log.info("---- PROCESSING CHUNK {}/{} ----", index, chunks.size());

            String finalPrompt = promptUser +
                    "\n\nBase Knowledge:\n" +
                    chunk +
                    "\n---";

            List<Map<String, Object>> messages = new ArrayList<>();

            messages.add(Map.of(
                    "role", "system",
                    "content", systemFeedback
            ));

            messages.add(Map.of(
                    "role", "user",
                    "content", finalPrompt
            ));

            Map<String, Object> request = new HashMap<>();
            Map<String, Object> options = new HashMap<>();

            options.put("temperature", 0);
            options.put("num_predict", 100);

            request.put("options", options);
            request.put("model", aiModel);
            request.put("messages", messages);
            request.put("stream", false);

            try {

                long startTime = System.currentTimeMillis();

                log.info("Calling AI API for chunk {}", index);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

                long duration = System.currentTimeMillis() - startTime;

                log.info("AI response received for chunk {} ({} ms)", index, duration);

                Map body = response.getBody();

                Map message = (Map) body.get("message");

                String content = message.get("content").toString();

                log.info(content);

            } catch (Exception e) {
                log.info("Error Occurred prompt AI {}", e.getMessage());
                throw new Exception(e.getMessage());
            }
        }
    }

    public List<String> chunkByLengthSize(List<String> data, int maxLength) {

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String item : data) {

            // kalau item sendiri lebih besar dari maxLength
            if (item.length() > maxLength) {

                // simpan current dulu kalau ada isi
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }

                // langsung jadi 1 chunk sendiri (tetap utuh)
                chunks.add(item);

                continue;
            }

            // kalau ditambah melebihi limit → simpan dulu
            if (current.length() + item.length() > maxLength) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
            }

            current.append(item).append("\n");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}