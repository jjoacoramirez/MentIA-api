package com.mentia.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mentia.catalog.domain.Exercise;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    Optional<Exercise> findByCode(String code);

    /** Un ejercicio solo cuenta como activo si su categoría también lo está. */
    @Query("""
            select e from Exercise e join fetch e.category c
            where c.code = :categoryCode and e.active = true and c.active = true
            order by e.code""")
    List<Exercise> findActiveByCategoryCode(@Param("categoryCode") String categoryCode);

    /** Inconsistencias de datos: ejercicios marcados activos dentro de una categoría inactiva. */
    @Query("select e from Exercise e join e.category c where e.active = true and c.active = false")
    List<Exercise> findActiveWithInactiveCategory();
}
