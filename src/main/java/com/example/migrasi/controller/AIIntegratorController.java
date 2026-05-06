package com.example.migrasi.controller;

import com.example.migrasi.dto.AIBasicResponse;
import com.example.migrasi.dto.AIRequest;
import com.example.migrasi.service.AIIntegratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIIntegratorController {

    private final AIIntegratorService aiIntegratorService;

    @PostMapping("/check-company-name")
    public ResponseEntity<AIBasicResponse> checkCompanyName(@RequestBody AIRequest request) {

        AIBasicResponse result = aiIntegratorService.checkCompanyName(request);

        return ResponseEntity.ok(result);
    }

}