package com.mentia.catalog;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.catalog.dto.GenerateContentRequest;
import com.mentia.catalog.dto.GenerateContentResponse;

import jakarta.validation.Valid;

/** Solo ADMIN (lo controla SecurityConfig con {@code /api/v1/admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/content")
public class AdminContentController {

    private final ContentGenerationService service;

    public AdminContentController(ContentGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public GenerateContentResponse generate(@Valid @RequestBody GenerateContentRequest request) {
        return service.generate(request.exerciseType(), request.count());
    }
}
