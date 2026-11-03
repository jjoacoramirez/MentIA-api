package com.mentia.performance;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.performance.dto.PerformanceProfileResponse;
import com.mentia.security.AuthenticatedUser;

@RestController
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping("/api/v1/profile/performance")
    public PerformanceProfileResponse performance(@AuthenticationPrincipal AuthenticatedUser user) {
        return performanceService.profile(user.userId());
    }
}
