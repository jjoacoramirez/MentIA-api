package com.mentia.catalog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.dto.CategoryResponse;
import com.mentia.catalog.dto.ExerciseDefinition;

/**
 * Consultas del catálogo para el resto de los módulos. Solo expone categorías y ejercicios activos.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CategoryRepository categoryRepository;
    private final ExerciseRepository exerciseRepository;

    public CatalogService(CategoryRepository categoryRepository, ExerciseRepository exerciseRepository) {
        this.categoryRepository = categoryRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public List<CategoryResponse> activeCategories() {
        return categoryRepository.findByActiveTrueOrderByMvpDescNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public Set<String> activeCategoryCodes() {
        return activeCategories().stream()
                .map(CategoryResponse::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<ExerciseDefinition> activeExercises(String categoryCode) {
        return exerciseRepository.findActiveByCategoryCode(categoryCode).stream()
                .map(ExerciseDefinition::from)
                .toList();
    }

    /**
     * Los ejercicios activos de una categoría inactiva no se usan (las consultas los filtran),
     * pero conviene enterarse: suele ser un error en una migración de contenido.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutInconsistentExercises() {
        List<String> inconsistent = exerciseRepository.findActiveWithInactiveCategory().stream()
                .map(Exercise::getCode)
                .toList();
        if (!inconsistent.isEmpty()) {
            log.warn("Ejercicios activos con categoría inactiva (no se van a usar): {}", inconsistent);
        }
    }
}
