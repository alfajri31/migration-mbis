package com.example.migrasi.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Component
public class PromptLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> loadPrompt(String fileName) {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("prompts/" + fileName)) {

            return mapper.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Gagal load prompt", e);
        }
    }
}