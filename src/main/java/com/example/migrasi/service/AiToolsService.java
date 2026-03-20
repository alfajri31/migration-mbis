package com.example.migrasi.service;

import com.example.migrasi.AI.AiBaseKnowledgeService;
import com.example.migrasi.AI.AiPageParserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class AiToolsService {

    private final DatabaseExportService databaseExportService;
    private final AiBaseKnowledgeService aiBaseKnowledgeService;
    private final AiPageParserService aiPageParserService;
    private Map<String,List<String>> basesKnowledge;

    public void sync() throws Exception {

        String jsonString = databaseExportService.exportDatabase(Set.of("log_table"));

        HashMap<String,List<String>> map = new HashMap<>();

        map.put("ini database saya", addDetailsKnowledge(jsonString));
        map.put("ada field kosong di gambar tolong bantu field mana yang belum tersimpan ke db saya",addImagesKnowledges());

        basesKnowledge.putAll(map);

        aiBaseKnowledgeService.processKnowledgeBase(basesKnowledge);
    }

    private List<String> addDetailsKnowledge(String jsonString) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        List<Map<String, Object>> list =
                mapper.readValue(jsonString, new TypeReference<>() {});

        List<String> detailsKnowledge = new ArrayList<>();

        for (Map<String, Object> item : list) {
            detailsKnowledge.add(mapper.writeValueAsString(item));
        }

        return detailsKnowledge;
    }

    private List<String> addImagesKnowledges() throws JsonProcessingException {

        List<Map<String,String>> list = aiPageParserService
                .toListMapFromDirectory("C:\\Users\\alfaj\\Projects\\mbis\\migration-mbis\\src\\main\\resources\\files");

        ObjectMapper mapper = new ObjectMapper();

        List<String> detailsKnowledge = new ArrayList<>();

        for (Map<String, String> item : list) {
            detailsKnowledge.add(mapper.writeValueAsString(item));
        }

        return detailsKnowledge;
    }

    private List<String> addCsvKnowledges(List<String> scanCsvFiles) {
        return null;
    }

    private List<String> addExcelKnowledges(List<String> scanExcelFiles) {
        return null;
    }



}
