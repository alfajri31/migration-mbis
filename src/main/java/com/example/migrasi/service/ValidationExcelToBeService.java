package com.example.migrasi.service;

import com.example.migrasi.AI.AiBaseKnowledgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class ValidationExcelToBeService {

    private final DatabaseImportService databaseImportService;
    private final AiBaseKnowledgeService aiBaseKnowledgeService;
    private Map<String,List<String>> basesKnowledge;
    private ExcelImportService excelImportService;

    public void sync() throws Exception {


        String jsonStringDb = databaseImportService.exportDatabase(Set.of("log_table"));

        String jsonStringExcel = excelImportService.scanDirectoryAsJson("C:\\Users\\alfaj\\Projects\\mbis\\migration-mbis\\src\\main\\resources\\excel");

        HashMap<String,List<String>> map = new HashMap<>();

        map.put("schema_db", addDetailsKnowledge(jsonStringDb));

        map.put("client_old_data_version",addDetailsExcelKnowledge(jsonStringExcel));

        basesKnowledge.putAll(map);

        aiBaseKnowledgeService.processKnowledgeBase(basesKnowledge,"client2be");
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

    private List<String> addDetailsExcelKnowledge(String jsonString) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();
        List<String> result = new ArrayList<>();

        JsonNode root = mapper.readTree(jsonString);

        extractNodes(root, mapper, result);

        return result;
    }

    private void extractNodes(JsonNode node, ObjectMapper mapper, List<String> result) throws JsonProcessingException {

        if (node.isArray()) {
            for (JsonNode child : node) {
                extractNodes(child, mapper, result);
            }

        } else if (node.isObject()) {

            // kalau object berisi value primitive → anggap data
            boolean isDataNode = true;
            for (JsonNode child : node) {
                if (child.isContainerNode()) {
                    isDataNode = false;
                    break;
                }
            }

            if (isDataNode) {
                result.add(mapper.writeValueAsString(node));
            } else {
                for (JsonNode child : node) {
                    extractNodes(child, mapper, result);
                }
            }
        }
    }



}
