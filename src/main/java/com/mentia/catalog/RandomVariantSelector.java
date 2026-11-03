package com.mentia.catalog;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.domain.ExerciseVariant;
import com.mentia.catalog.dto.VariantDefinition;

/**
 * Elección uniforme entre las variantes activas. Implementación provisoria hasta la rotación de BACK-11.
 */
@Component
public class RandomVariantSelector implements VariantSelector {

    private final ExerciseVariantRepository repository;
    private final RandomGenerator random;

    public RandomVariantSelector(ExerciseVariantRepository repository, RandomGenerator random) {
        this.repository = repository;
        this.random = random;
    }

    @Override
    @Transactional(readOnly = true)
    public VariantDefinition pick(UUID userId, UUID exerciseId, int level) {
        List<ExerciseVariant> variants = repository.findByExerciseIdAndActiveTrueOrderByVariantKey(exerciseId);
        if (variants.isEmpty()) {
            // Un ejercicio activo sin variantes activas es un error de contenido, no del usuario.
            throw new IllegalStateException("El ejercicio " + exerciseId + " no tiene variantes activas");
        }
        return VariantDefinition.from(variants.get(random.nextInt(variants.size())));
    }
}
