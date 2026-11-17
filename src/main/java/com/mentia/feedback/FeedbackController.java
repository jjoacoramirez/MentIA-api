package com.mentia.feedback;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.feedback.dto.FeedbackResponse;
import com.mentia.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/sessions")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    /** POST porque puede generar el comentario (y gastar cuota de IA) la primera vez; después devuelve el guardado. */
    @PostMapping("/{sessionId}/feedback")
    public FeedbackResponse feedback(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return service.getOrCreate(user.userId(), sessionId);
    }
}
