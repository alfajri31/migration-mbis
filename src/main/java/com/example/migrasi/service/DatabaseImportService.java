package com.example.migrasi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class DatabaseImportService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Value("${ai.model.data.crawl.limit:5}")
    private int limit;

    public DatabaseImportService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public String exportDatabase(Set<String> excludedTables) {

        List<Map<String, Object>> database = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {

            DatabaseMetaData meta = conn.getMetaData();

            // 🔥 ambil SEMUA table tanpa schema restriction
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {

                String schema = tables.getString("TABLE_SCHEM");
                String tableName = tables.getString("TABLE_NAME");

                if (excludedTables != null && excludedTables.contains(tableName)) {
                    continue;
                }

                Map<String, Object> tableJson = new LinkedHashMap<>();
                tableJson.put("schema", schema);
                tableJson.put("table", tableName);
                tableJson.put("ddl", getTableDDL(meta, schema, tableName));
                tableJson.put("rows", getTableData(conn, schema, tableName, limit));

                database.add(tableJson);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error exporting database", e);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(database);
        } catch (Exception e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }

    // =========================
    // 🔥 DDL UNIVERSAL
    // =========================
    private Map<String, Object> getTableDDL(DatabaseMetaData meta, String schema, String tableName) {

        Map<String, Object> ddl = new LinkedHashMap<>();

        try {
            // Columns
            List<Map<String, Object>> columns = new ArrayList<>();
            ResultSet cols = meta.getColumns(null, schema, tableName, null);

            while (cols.next()) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("name", cols.getString("COLUMN_NAME"));
                col.put("type", cols.getString("TYPE_NAME"));
                col.put("size", cols.getInt("COLUMN_SIZE"));
                col.put("nullable", cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable);

                columns.add(col);
            }
            ddl.put("columns", columns);

        } catch (Exception e) {
            ddl.put("columns_error", e.getMessage());
        }

        try {
            // Primary Keys
            List<String> pkList = new ArrayList<>();
            ResultSet pk = meta.getPrimaryKeys(null, schema, tableName);

            while (pk.next()) {
                pkList.add(pk.getString("COLUMN_NAME"));
            }
            ddl.put("primaryKey", pkList);

        } catch (Exception e) {
            ddl.put("pk_error", e.getMessage());
        }

        try {
            // Foreign Keys
            List<Map<String, Object>> fks = new ArrayList<>();
            ResultSet fk = meta.getImportedKeys(null, schema, tableName);

            while (fk.next()) {
                Map<String, Object> fkMap = new LinkedHashMap<>();
                fkMap.put("column", fk.getString("FKCOLUMN_NAME"));
                fkMap.put("refTable", fk.getString("PKTABLE_NAME"));
                fkMap.put("refColumn", fk.getString("PKCOLUMN_NAME"));
                fks.add(fkMap);
            }
            ddl.put("foreignKeys", fks);

        } catch (Exception e) {
            ddl.put("fk_error", e.getMessage());
        }

        return ddl;
    }

    // =========================
    // 🔥 DATA UNIVERSAL (SUPER SAFE)
    // =========================
    private List<Map<String, Object>> getTableData(Connection conn, String schema, String tableName, int limit) {

        List<Map<String, Object>> rows = new ArrayList<>();

        try {

            DatabaseMetaData meta = conn.getMetaData();

            // 🔥 universal quote (penting banget)
            String quote = meta.getIdentifierQuoteString();
            if (quote == null || quote.trim().isEmpty()) {
                quote = "\""; // fallback
            }

            String fullTableName;

            if (schema != null && !schema.isEmpty()) {
                fullTableName = quote + schema + quote + "." + quote + tableName + quote;
            } else {
                fullTableName = quote + tableName + quote;
            }

            String query = "SELECT * FROM " + fullTableName;

            try (Statement stmt = conn.createStatement()) {

                // 🔥 UNIVERSAL LIMIT
                stmt.setMaxRows(limit);

                try (ResultSet rs = stmt.executeQuery(query)) {

                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int columnCount = rsMeta.getColumnCount();

                    while (rs.next()) {

                        Map<String, Object> row = new LinkedHashMap<>();

                        for (int i = 1; i <= columnCount; i++) {
                            String colName = rsMeta.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(colName, value);
                        }

                        rows.add(row);
                    }
                }
            }

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to fetch data: " + e.getMessage());
            rows.add(error);
        }

        return rows;
    }
}