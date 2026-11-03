package com.mentia.session;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.security.AuthenticatedUser;
import com.mentia.session.dto.CreateSessionRequest;
import com.mentia.session.dto.SessionResponse;
import com.mentia.session.dto.SessionResultResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionGenerationService generationService;
    private final SessionLifecycleService lifecycleService;

    public SessionController(SessionService sessionService, SessionGenerationService generationService,
            SessionLifecycleService lifecycleService) {
        this.sessionService = sessionService;
        this.generationService = generationService;
        this.lifecycleService = lifecycleService;
    }

    /** 201 si se generó una sesión; 200 si se devuelve una abierta para retomarla. El body es opcional. */
    @PostMapping
    public ResponseEntity<SessionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        Integer minutes = request != null ? request.durationMinutes() : null;
        SessionGenerationService.Generated generated = generationService.create(user.userId(), minutes);
        return ResponseEntity.status(generated.created() ? HttpStatus.CREATED : HttpStatus.OK).body(generated.session());
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return sessionService.get(user.userId(), sessionId);
    }

    @PostMapping("/{sessionId}/start")
    public SessionResponse start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return sessionService.start(user.userId(), sessionId);
    }

    @PostMapping("/{sessionId}/complete")
    public SessionResultResponse complete(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID sessionId) {
        return lifecycleService.complete(user.userId(), sessionId);
    }
}
