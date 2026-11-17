package com.mentia.attempt.dto;

import java.time.Instant;
import java.util.UUID;

import com.mentia.attempt.domain.Attempt;

public record AttemptResponse(UUID attemptId, Instant recordedAt) {

    public static AttemptResponse from(Attempt attempt) {
        return new AttemptResponse(attempt.getId(), attempt.getCreatedAt());
    }
}
