package com.example.migrasi.AI;

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
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiBaseKnowledgeService {

    @Value("${agent.host.url}")
    private String url;

    @Value("${ai.model.base.knowledge}")
    private String aiModel;

    @Value("${ai.model.context.base.knowledge}")
    private int contextWindow;

    private final RestTemplate restTemplate = new RestTemplate();

    public void processKnowledgeBase(Map<String, List<String>> data) throws Exception {

        data = data.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> "rel_" + entry.getKey()
                                .toLowerCase()
                                .trim()
                                .replaceAll("\\s+", "_"),
                        Map.Entry::getValue,
                        (a, b) -> a // handle duplicate
                ));

        int safeContextWindow = contextWindow - 500;

        int totalChunkCounts = 0;

        int totalLength = 0;

        for (List<String> list : data.values()) {

            if(list.toArray().length < safeContextWindow) {

                totalLength+= list.toArray().length;

            }
            totalChunkCounts += list.size();
        }

        int estimationSummaryTokens = totalChunkCounts * (totalLength * 3);

        if (estimationSummaryTokens >= safeContextWindow) {
            log.warn("Can't be proceed: tokens will be overflow");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        List<String> partialSummaries = new ArrayList<>();

        log.info("START MAP PHASE (per chunk processing)");

        int index=0;

        for (Map.Entry<String, List<String>> entry : data.entrySet()) {

            String key = entry.getKey();
            List<String> chunks = entry.getValue();

            log.info("total all size chunks: {} ",chunks.size());

            for (String chunk : chunks) {

                String jsonChunk = mapper.writeValueAsString(
                        Map.of(key, mapper.readValue("[" + chunk + "]", List.class))
                );

                int estimatedTokens = estimateTokens(jsonChunk);

                log.warn("Chunk tokens={}", estimatedTokens);

                if (estimatedTokens > safeContextWindow) {
                    log.warn("Chunk over context window, splitting... tokens={} SKIP! I assume this only data insert remnants", estimatedTokens);
                    continue;

                } else {
                    partialSummaries.add(jsonChunk);
                }
                index++;
            }
        }

        log.info("MAP PHASE DONE. Total partial summaries: {}", partialSummaries.size());

        mapper = new ObjectMapper();

        ObjectMapper finalMapper = mapper;

        List<Object> parsed = partialSummaries.stream()
                .map(s -> {
                    try {
                        return finalMapper.readValue(s, Object.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        String reducePrompt = mapper.writeValueAsString(
                Map.of(
                        "task", "Analisis Data Detail Berbasis Field dan Struktur",
                        "instruction", String.join(" ",
                                "Analisis seluruh data yang diberikan secara mendalam hingga level field dan struktur.",
                                "Identifikasi peran masing-masing rel_ secara kontekstual pada nama rel_ seperti 'rel_database' artinya relevansi dengan database yang berjalan saat ini,",
                                "kemudian lakukan perbandingan di masing-masing rel_ untuk menemukan perbedaan, kesalahan, atau potensi masalah.",
                                "Fokuskan analisis pada level field, tipe data, struktur, dan konsistensi syntax."
                        ),
                        "rules", List.of(
                                "1. lakukan identifikasi masalah di masing-masing rel_",
                                "2. Identifikasi peran data berdasarkan struktur dan isi.",
                                "3. Lakukan analisis hingga level field/kolom, bukan hanya level object.",
                                "4. Sebutkan secara eksplisit nama field yang bermasalah.",
                                "5. Validasi tipe data antar data (misal: string vs number vs boolean).",
                                "6. Identifikasi field yang missing, null, atau tidak sesuai.",
                                "7. Identifikasi perbedaan struktur (schema mismatch).",
                                "8. Gunakan bahasa Indonesia.",
                                "9. Jika ada ambiguitas, jelaskan secara spesifik field mana yang ambigu.",
                                "10. Berikan rekomendasi perbaikan yang spesifik per field."
                        ),
                        "analysis_focus", List.of(
                                "Field existence (apakah field ada atau tidak)",
                                "Field type consistency",
                                "Nullability / missing values",
                                "Naming consistency",
                                "Struktur object / nested",
                                "Constraint implicit (unik, relasi, dll)"
                        ),
                        "expected_output", Map.of(
                                "data_role_identification", Map.of(
                                        "existing_state", "Data kondisi saat ini",
                                        "incoming_data", "Data baru"
                                ),
                                "summary", "Ringkasan hasil analisis",
                                "field_level_analysis", List.of(
                                        Map.of(
                                                "field_name", "Nama field",
                                                "issue_type", "Jenis masalah (missing, type mismatch, dll)",
                                                "description", "Penjelasan detail",
                                                "location", "Letak field dalam struktur data",
                                                "suggestion", "Rekomendasi perbaikan"
                                        )
                                ),
                                "structure_issues", List.of(
                                        "Perbedaan struktur antar data"
                                ),
                                "key_findings", List.of("Insight penting"),
                                "recommendations", List.of("Rekomendasi global"),
                                "ambiguities", "Ambiguitas jika ada"
                        ),
                        "data", parsed
                )
        );

        String finalResult = callSummary(reducePrompt,index, data.size());

        log.info("FINAL RESULT:\n{}", finalResult);

        saveToFile(finalResult);
    }

    private String callSummary(String reducePrompt,int chunkIndex,int totalChunkSize) {

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content",
                "Anda adalah asisten analisis untuk pekerjaan saya. " +
                        "Berikan informasi secara faktual dan terstruktur. " +
                        "Gunakan bahasa Indonesia formal. " +
                        "Dilarang menambahkan informasi di luar input."
        ));

        messages.add(Map.of(
                "role", "user",
                "content", reducePrompt
        ));

        Map<String, Object> request = new HashMap<>();

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

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        return text.length() / 3;
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

}