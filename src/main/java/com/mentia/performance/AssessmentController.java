package com.mentia.performance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.security.AuthenticatedUser;
import com.mentia.session.dto.SessionResponse;

@RestController
@RequestMapping("/api/v1/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** 201 si se creó una evaluación; 200 si se devuelve una existente para retomarla. */
    @PostMapping("/start")
    public ResponseEntity<SessionResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean restart) {
        AssessmentService.Started started = assessmentService.start(user.userId(), restart);
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK).body(started.session());
    }

    /** Solo ADMIN (lo controla SecurityConfig). Herramienta de debug. */
    @PostMapping("/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skip(@AuthenticationPrincipal AuthenticatedUser user) {
        assessmentService.skip(user.userId());
    }
}
