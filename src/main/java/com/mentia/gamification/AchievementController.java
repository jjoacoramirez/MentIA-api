package com.mentia.gamification;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.gamification.dto.AchievementResponse;
import com.mentia.security.AuthenticatedUser;

@RestController
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping("/api/v1/achievements")
    public List<AchievementResponse> achievements(@AuthenticationPrincipal AuthenticatedUser user) {
        return achievementService.list(user.userId());
    }
}
