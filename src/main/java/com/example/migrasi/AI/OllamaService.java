package com.example.migrasi.AI;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OllamaService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${agent.host.url.chat.text}")
    private String url;

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    // =========================
    // PUBLIC METHOD
    // =========================
    public Map<String, Object> generate(String prompt, String systemPrompt) {
        Map<String, Object> request = buildGenerateRequest(prompt, systemPrompt);
        return callOllama(request);
    }

    public Map<String, Object> chat(String prompt, String systemPrompt) {
        Map<String, Object> request = buildChatRequest(prompt, systemPrompt);
        return callOllama(request);
    }

    // =========================
    // PRIVATE BUILDER
    // =========================
    private Map<String, Object> buildGenerateRequest(String prompt, String systemPrompt) {

        String finalPrompt =
                "System:\n" + systemPrompt + "\n\n" +
                        "User:\n" + prompt;

        Map<String, Object> request = baseRequest();

        request.put("prompt", finalPrompt);

        return request;
    }

    private Map<String, Object> buildChatRequest(String prompt, String systemPrompt) {

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", systemPrompt
        ));

        messages.add(Map.of(
                "role", "user",
                "content", prompt
        ));

        Map<String, Object> request = baseRequest();
        request.put("messages", messages);

        return request;
    }

    private Map<String, Object> baseRequest() {
        Map<String, Object> request = new HashMap<>();

        request.put("model", aiModel);
        request.put("stream", false);
        request.put("options", Map.of("temperature", 0));

        return request;
    }

    // =========================
    // CALL API
    // =========================
    private Map<String, Object> callOllama(Map<String, Object> request) {
        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, request, Map.class);

        return response.getBody();
    }
}
