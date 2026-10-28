package com.mentia.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.catalog.domain.Exercise;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    Optional<Exercise> findByCode(String code);

    List<Exercise> findByCategoryCodeAndActiveTrue(String categoryCode);
}
