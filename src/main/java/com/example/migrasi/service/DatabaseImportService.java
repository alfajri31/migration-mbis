package com.example.migrasi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class DatabaseImportService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseImportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String exportDatabase(Set<String> excludedTables) {

        List<Map<String, Object>> database = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {

            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                if (excludedTables.contains(tableName)) {
                    continue;
                }

                Map<String, Object> tableJson = new LinkedHashMap<>();
                tableJson.put("table", tableName);
                tableJson.put("ddl", getTableDDL(meta, tableName));
                tableJson.put("rows", getTableData(conn, tableName));

                database.add(tableJson);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(database);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> getTableDDL(DatabaseMetaData meta, String tableName) throws SQLException {

        Map<String, Object> ddl = new LinkedHashMap<>();

        // Columns
        List<Map<String, Object>> columns = new ArrayList<>();
        ResultSet cols = meta.getColumns(null, null, tableName, null);

        while (cols.next()) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", cols.getString("COLUMN_NAME"));
            col.put("type", cols.getString("TYPE_NAME"));
            col.put("size", cols.getInt("COLUMN_SIZE"));
            col.put("nullable", cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable);

            columns.add(col);
        }

        ddl.put("columns", columns);

        // Primary Key
        List<String> pkList = new ArrayList<>();
        ResultSet pk = meta.getPrimaryKeys(null, null, tableName);

        while (pk.next()) {
            pkList.add(pk.getString("COLUMN_NAME"));
        }

        ddl.put("primaryKey", pkList);

        // Foreign Keys
        List<Map<String, Object>> fks = new ArrayList<>();
        ResultSet fk = meta.getImportedKeys(null, null, tableName);

        while (fk.next()) {

            Map<String, Object> fkMap = new LinkedHashMap<>();

            fkMap.put("column", fk.getString("FKCOLUMN_NAME"));

            fkMap.put("refTable", fk.getString("PKTABLE_NAME"));

            fkMap.put("refColumn", fk.getString("PKCOLUMN_NAME"));

            fks.add(fkMap);
        }

        ddl.put("foreignKeys", fks);

        return ddl;
    }

    private List<Map<String, Object>> getTableData(Connection conn, String tableName) throws SQLException {

        List<Map<String, Object>> rows = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }

                rows.add(row);
            }
        }

        return rows;
    }

}