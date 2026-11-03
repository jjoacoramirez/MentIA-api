package com.mentia.gamification;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.gamification.domain.SessionReward;

public interface SessionRewardRepository extends JpaRepository<SessionReward, UUID> {
}
