package com.example.migrasi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class ExcelImportService {

    private final ObjectMapper objectMapper;

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
                                result.put(fileName, readExcel(path.toString()));
                            } else if (fileName.endsWith(".csv")) {
                                result.put(fileName, readCsv(path.toFile()));
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
    private Map<String, List<Map<String, String>>> readExcel(String filePath) throws Exception {

        Map<String, List<Map<String, String>>> result = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {

                Sheet sheet = workbook.getSheetAt(s);
                List<Map<String, String>> sheetData = new ArrayList<>();

                int startRow = detectHeaderRow(sheet); // auto detect

                if (startRow == -1) continue;

                Row headerRow = sheet.getRow(startRow);

                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(cell.toString());
                }

                for (int i = startRow + 1; i <= sheet.getLastRowNum(); i++) {
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
    private List<Map<String, String>> readCsv(File file) throws Exception {

        List<Map<String, String>> result = new ArrayList<>();

        try (Reader reader = new FileReader(file);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {

                Map<String, String> row = new HashMap<>();

                for (String header : csvParser.getHeaderNames()) {
                    row.put(header, record.get(header));
                }

                result.add(row);
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