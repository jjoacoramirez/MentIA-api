package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseLevel;
import com.mentia.catalog.domain.ExerciseVariant;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.support.IntegrationTest;

@Transactional
class VariantSeedIntegrationTest extends IntegrationTest {

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    ExerciseLevelRepository levels;

    @Autowired
    ExerciseVariantRepository variants;

    @Autowired
    VariantSelector selector;

    @Test
    void cadaEjercicioActivoTieneAlMenosTresVariantesActivasConContenido() {
        for (Exercise exercise : exercises.findAll()) {
            if (!exercise.isActive()) {
                continue;
            }
            List<ExerciseVariant> active = variants.findByExerciseIdAndActiveTrueOrderByVariantKey(exercise.getId());
            assertThat(active).as("variantes de %s", exercise.getCode()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(active).allSatisfy(v -> assertThat(v.getContent()).isNotEmpty());
        }
    }

    @Test
    void elReconocimientoVisualTieneEstimulosSuficientesParaElNivelMaximo() {
        Exercise recognition = exercises.findByCode("VISUAL_RECOGNITION").orElseThrow();
        ExerciseLevel max = levels.findByIdExerciseIdOrderByIdLevel(recognition.getId()).getLast();
        int needed = ((Number) max.getParams().get("itemsToMemorize")).intValue()
                + ((Number) max.getParams().get("distractors")).intValue();

        for (ExerciseVariant variant : variants.findByExerciseIdAndActiveTrueOrderByVariantKey(recognition.getId())) {
            assertThat((List<?>) variant.getContent().get("items")).as(variant.getVariantKey())
                    .hasSizeGreaterThanOrEqualTo(needed)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void elSelectorDevuelveUnaVarianteDelEjercicio() {
        Exercise nBack = exercises.findByCode("N_BACK").orElseThrow();

        VariantDefinition picked = selector.pick(UUID.randomUUID(), nBack.getId(), 1);

        assertThat(picked.exerciseId()).isEqualTo(nBack.getId());
        assertThat(picked.content()).containsKey("symbols");
    }
}
