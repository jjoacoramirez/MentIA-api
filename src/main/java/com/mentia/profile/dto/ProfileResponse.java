package com.mentia.profile.dto;

import java.util.List;

import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.EducationLevel;
import com.mentia.profile.domain.TrainingProfile;

public record ProfileResponse(
        AgeRange ageRange,
        EducationLevel educationLevel,
        int sessionMinutes,
        List<String> goals,
        boolean onboardingCompleted,
        boolean assessmentCompleted) {

    public static ProfileResponse from(TrainingProfile profile, boolean assessmentCompleted) {
        return new ProfileResponse(profile.getAgeRange(), profile.getEducationLevel(), profile.getSessionMinutes(),
                profile.getGoals(), profile.isOnboardingCompleted(), assessmentCompleted);
    }
}
