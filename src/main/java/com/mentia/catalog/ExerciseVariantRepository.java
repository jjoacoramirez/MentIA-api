package com.mentia.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.catalog.domain.ExerciseVariant;

public interface ExerciseVariantRepository extends JpaRepository<ExerciseVariant, UUID> {

    List<ExerciseVariant> findByExerciseIdAndActiveTrueOrderByVariantKey(UUID exerciseId);
}
