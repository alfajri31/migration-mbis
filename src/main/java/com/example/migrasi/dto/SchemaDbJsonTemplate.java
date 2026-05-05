package com.example.migrasi.dto;

import java.util.List;

//Tabel "users" (menyimpan data pengguna sistem) memiliki kolom: id (integer, not null, identitas unik pengguna); full_name (string, not null, nama lengkap pengguna); email (string, not null, alamat email pengguna); phone_number (string, nullable, nomor telepon pengguna).
public class SchemaDbJsonTemplate {
    public String tableName;
    public String description;

    public List<Column> columns;

    public static class Column {
        public String name;
        public String dataType;
        public boolean nullable;
        public String description;
    }
}