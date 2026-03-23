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

        map.put("client_data",addDetailsExcelKnowledge(jsonStringExcel));

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

        JsonNode root = mapper.readTree(jsonString);

        // ambil semua data row dulu
        List<Map<String, Object>> rows = new ArrayList<>();

        extractNodes(root, mapper, rows);

        // GROUP BY STRUKTUR KOLOM
        Map<Set<String>, List<Map<String, Object>>> grouped = new HashMap<>();

        for (Map<String, Object> row : rows) {

            Set<String> keys = row.keySet();

            grouped.computeIfAbsent(keys, k -> new ArrayList<>()).add(row);
        }

        // CONVERT KE FORMAT AI
        List<String> result = new ArrayList<>();

        for (Map.Entry<Set<String>, List<Map<String, Object>>> entry : grouped.entrySet()) {

            Map<String, Object> tableCandidate = new LinkedHashMap<>();

            tableCandidate.put("client_columns", entry.getKey());

            List<Map<String, Object>> sampleRows = entry.getValue()
                    .stream()
                    .limit(5)
                    .toList();

            tableCandidate.put("sample_data", sampleRows);

            result.add(mapper.writeValueAsString(tableCandidate));
        }

        return result;
    }

    private void extractNodes(JsonNode node, ObjectMapper mapper, List<Map<String, Object>> result) {

        if (node.isArray()) {
            for (JsonNode child : node) {
                extractNodes(child, mapper, result);
            }

        } else if (node.isObject()) {

            boolean isDataNode = true;
            for (JsonNode child : node) {
                if (child.isContainerNode()) {
                    isDataNode = false;
                    break;
                }
            }

            if (isDataNode) {
                Map<String, Object> map = mapper.convertValue(node, new TypeReference<>() {});
                result.add(map);
            } else {
                for (JsonNode child : node) {
                    extractNodes(child, mapper, result);
                }
            }
        }
    }



}
