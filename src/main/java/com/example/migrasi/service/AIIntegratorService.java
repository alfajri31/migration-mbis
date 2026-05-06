package com.example.migrasi.service;

import com.example.migrasi.dto.AIBasicResponse;
import com.example.migrasi.dto.AIRequest;

public interface AIIntegratorService {
    AIBasicResponse checkCompanyName(AIRequest request);
}
