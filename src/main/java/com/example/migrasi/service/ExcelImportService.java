package com.example.migrasi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ExcelImportService {

    private final ObjectMapper objectMapper;

    public ExcelImportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Value("${ai.data.crawl.limit:5}")
    private int limit;

    // =========================
    // MAIN METHOD (SCAN DIRECTORY)
    // =========================

    public String scanDirectoryAsJson(String dirPath) throws Exception {
        Map<String, Object> data = scanDirectory(dirPath);
        return objectMapper.writeValueAsString(data);
    }

    public Map<String, Object> scanDirectory(String dirPath) throws Exception {

        Map<String, Object> result = new HashMap<>();

        try (Stream<Path> paths = Files.walk(Paths.get(dirPath))) {

            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();

                        try {
                            if (fileName.endsWith(".xlsx")) {
                                result.put(fileName, readExcel(path.toString(), limit));
                            } else if (fileName.endsWith(".csv")) {
                                result.put(fileName, readCsv(path.toFile(), limit));
                            }
                        } catch (Exception e) {
                            result.put(fileName, "ERROR: " + e.getMessage());
                        }
                    });
        }

        return result;
    }

    // =========================
    // EXCEL READER (MULTI SHEET)
    // =========================
    private Map<String, List<Map<String, String>>> readExcel(String filePath, int limit) throws Exception {

        Map<String, List<Map<String, String>>> result = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {

                Sheet sheet = workbook.getSheetAt(s);
                List<Map<String, String>> sheetData = new ArrayList<>();

                int startRow = detectHeaderRow(sheet);
                if (startRow == -1) continue;

                Row headerRow = sheet.getRow(startRow);

                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(cell.toString());
                }

                // 🔥 BATASI SAMPAI LIMIT
                int endRow = Math.min(startRow + limit, sheet.getLastRowNum());

                for (int i = startRow + 1; i <= endRow; i++) {

                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    Map<String, String> rowData = new HashMap<>();

                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        rowData.put(headers.get(j), cell.toString());
                    }

                    sheetData.add(rowData);
                }

                result.put(sheet.getSheetName(), sheetData);
            }
        }

        return result;
    }

    // =========================
    // CSV READER
    // =========================
    private List<Map<String, String>> readCsv(File file, int limit) throws Exception {

        List<Map<String, String>> result = new ArrayList<>();

        try (Reader reader = new FileReader(file);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            int count = 0;

            for (CSVRecord record : csvParser) {

                if (count >= limit) break;

                Map<String, String> row = new HashMap<>();

                for (String header : csvParser.getHeaderNames()) {
                    row.put(header, record.get(header));
                }

                result.add(row);
                count++;
            }
        }

        return result;
    }

    // =========================
    // AUTO DETECT HEADER ROW
    // =========================
    private int detectHeaderRow(Sheet sheet) {

        for (Row row : sheet) {
            if (row.getPhysicalNumberOfCells() > 2) {
                return row.getRowNum();
            }
        }

        return -1;
    }
}