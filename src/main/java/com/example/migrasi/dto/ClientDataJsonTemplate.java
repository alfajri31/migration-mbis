package com.example.migrasi.dto;

import java.util.List;

//Data ini berasal dari sheet "Customer Data" (informasi terkait pelanggan) dengan field: customer_name (string, nama lengkap pelanggan, contoh: Budi Santoso); email (string, alamat email pelanggan, contoh: budi@email.com); phone_number (string, nomor telepon pelanggan, contoh: 08123456789).
public class ClientDataJsonTemplate {
    public String sheetName;
    public String description;

    public List<Field> fields;

    public static class Field {
        public String name;
        public String valueType;
        public String exampleValue;
        public String description;
    }
}