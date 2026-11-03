package com.mentia.progress;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.progress.dto.CategoryProgress;
import com.mentia.progress.dto.ProgressSummary;
import com.mentia.progress.dto.SessionHistoryPage;
import com.mentia.security.AuthenticatedUser;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@RestController
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /** Historial de sesiones, más recientes primero. */
    @GetMapping("/api/v1/sessions")
    public SessionHistoryPage sessions(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño mínimo es 1")
            @Max(value = 50, message = "El tamaño máximo es 50") int size) {
        return progressService.sessions(user.userId(), page, size);
    }

    @GetMapping("/api/v1/progress/summary")
    public ProgressSummary summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return progressService.summary(user.userId());
    }

    @GetMapping("/api/v1/progress/categories")
    public List<CategoryProgress> categories(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "week")
            @Pattern(regexp = "week|month", message = "La granularidad es week o month") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return progressService.categories(user.userId(), granularity, from, to);
    }
}
