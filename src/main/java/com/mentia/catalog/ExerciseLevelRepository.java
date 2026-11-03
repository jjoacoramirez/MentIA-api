package com.mentia.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.catalog.domain.ExerciseLevel;

public interface ExerciseLevelRepository extends JpaRepository<ExerciseLevel, ExerciseLevel.Key> {

    List<ExerciseLevel> findByIdExerciseIdOrderByIdLevel(UUID exerciseId);
}
