package com.mentia.gamification;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.gamification.domain.UserProgress;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
}
