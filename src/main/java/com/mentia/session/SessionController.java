package com.mentia.session;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.security.AuthenticatedUser;
import com.mentia.session.dto.SessionResponse;
import com.mentia.session.dto.SessionResultResponse;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class SessionController {

    private final SessionService sessionService;
    private final SessionLifecycleService lifecycleService;

    public SessionController(SessionService sessionService, SessionLifecycleService lifecycleService) {
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/start")
    public SessionResponse start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return sessionService.start(user.userId(), sessionId);
    }

    @PostMapping("/complete")
    public SessionResultResponse complete(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID sessionId) {
        return lifecycleService.complete(user.userId(), sessionId);
    }
}
