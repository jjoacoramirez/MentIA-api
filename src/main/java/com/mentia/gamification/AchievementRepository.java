package com.mentia.gamification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.gamification.domain.Achievement;

public interface AchievementRepository extends JpaRepository<Achievement, String> {

    List<Achievement> findByActiveTrueOrderByPosition();
}
