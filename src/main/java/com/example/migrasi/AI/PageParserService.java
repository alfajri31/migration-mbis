package com.example.migrasi.AI;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
public class PageParserService {

    @Value("${ai.model.picture}")
    private String aiModel;

    @Value("${prompt.user}")
    private String promptUser;

    @Value("${system.feedback}")
    private String systemFeedback;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, String>> toListMap(String path) {

        String url = "http://localhost:11434/api/chat";

        List<Map<String, Object>> messages = new ArrayList<>();

        // system message
        messages.add(Map.of(
                "role", "system",
                "content", systemFeedback
        ));

        // user message (WITH IMAGE)
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", promptUser);
        userMessage.put("images", List.of(encodeImageToBase64(path)));

        messages.add(userMessage);

        Map<String, Object> request = new HashMap<>();

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0);

        request.put("options", options);
        request.put("model", aiModel);
        request.put("messages", messages);
        request.put("stream", false);
        request.put("format", "json");

        String result = "";

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            Map body = response.getBody();

            if (body == null) {
                throw new RuntimeException("Response body null dari Ollama");
            }

            Map message = (Map) body.get("message");

            if (message == null) {
                throw new RuntimeException("Message null dari Ollama");
            }

            result = (String) message.get("content");

            log.info("RAW AI RESPONSE: {}", result);

        } catch (Exception e) {
            log.error("Error Occured prompt AI {}", e.getMessage(), e);
            throw new RuntimeException("Gagal call AI", e);
        }

        return extractFields(result);
    }

    private List<Map<String, String>> extractFields(String jsonString) {

        if (jsonString == null || jsonString.isBlank()) {
            throw new RuntimeException("AI response kosong!");
        }

        ObjectMapper mapper = new ObjectMapper();

        try {
//            jsonString = jsonString
//                    .replace("```json", "")
//                    .replace("```", "")
//                    .trim();

            jsonString = jsonString
                    .replaceAll("\\n", "")
                    .trim();

            // cek apakah array atau object
            if (jsonString.startsWith("[")) {
                return mapper.readValue(
                        jsonString,
                        new TypeReference<>() {
                        }
                );
            } else {
                // kalau object → bungkus jadi list
                Map<String, String> single = mapper.readValue(
                        jsonString,
                        new TypeReference<>() {
                        }
                );

                return List.of(single);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed parsing JSON: " + jsonString, e);
        }
    }

    private String encodeImageToBase64(String path) {
        try {
            byte[] fileContent = Files.readAllBytes(Paths.get(path));
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            throw new RuntimeException("Gagal encode image: " + path, e);
        }
    }
}