package com.mentia.gamification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.gamification.dto.GamificationState;
import com.mentia.security.AuthenticatedUser;

@RestController
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/api/v1/gamification/me")
    public GamificationState me(@AuthenticationPrincipal AuthenticatedUser user) {
        return gamificationService.state(user.userId());
    }
}
