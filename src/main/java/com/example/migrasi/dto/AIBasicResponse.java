package com.example.migrasi.dto;

import lombok.Builder;
import lombok.Data;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIBasicResponse {

    private String result;
    private boolean success;
    private String remark;

    public static AIBasicResponse success(String result) {
        return AIBasicResponse.builder()
                .result(result)
                .success(true)
                .remark("OK")
                .build();
    }

    public static AIBasicResponse failed(String remark) {
        return AIBasicResponse.builder()
                .success(false)
                .remark(remark)
                .build();
    }
}