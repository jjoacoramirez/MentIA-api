package com.mentia.attempt;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.attempt.dto.AttemptRequest;
import com.mentia.attempt.dto.AttemptResponse;
import com.mentia.security.AuthenticatedUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/attempts")
public class AttemptController {

    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** 201 la primera vez; 200 con el intento original si es un reintento. */
    @PostMapping
    public ResponseEntity<AttemptResponse> record(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID sessionId, @Valid @RequestBody AttemptRequest request) {
        AttemptService.Recorded recorded = attemptService.record(user.userId(), sessionId, request);
        return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK).body(recorded.attempt());
    }
}
