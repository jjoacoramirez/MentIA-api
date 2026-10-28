package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.domain.Category;
import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseLevel;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;
import com.mentia.support.IntegrationTest;

@Transactional
class CatalogSeedIntegrationTest extends IntegrationTest {

    @Autowired
    CategoryRepository categories;

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    ExerciseLevelRepository levels;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void siembraLasCategoriasConSuAlcance() {
        assertThat(categories.findAll())
                .extracting(Category::getCode, Category::isMvp, Category::isActive)
                .containsExactlyInAnyOrder(
                        tuple("ATENCION", true, true),
                        tuple("MEMORIA_VISUAL", true, true),
                        tuple("MEMORIA_TRABAJO", true, true),
                        tuple("MEMORIA_AUDITIVA", false, false));
    }

    @Test
    void siembraUnEjercicioPorMecanica() {
        List<Exercise> all = exercises.findAll();
        assertThat(all).extracting(Exercise::getType).containsExactlyInAnyOrder(ExerciseType.values());
        assertThat(all).allSatisfy(e -> assertThat(e.isPostMvp()).isFalse());

        Exercise auditory = exercises.findByCode("AUDITORY_SEQUENCE").orElseThrow();
        assertThat(auditory.isActive()).isFalse();
        assertThat(auditory.getModality()).isEqualTo(Modality.AUDITORY);
        assertThat(auditory.getCategory().getCode()).isEqualTo("MEMORIA_AUDITIVA");
    }

    @Test
    void filtraEjerciciosActivosPorCategoria() {
        assertThat(exercises.findByCategoryCodeAndActiveTrue("MEMORIA_TRABAJO"))
                .extracting(Exercise::getType)
                .containsExactlyInAnyOrder(ExerciseType.REVERSE_SEQUENCE, ExerciseType.N_BACK);
        assertThat(exercises.findByCategoryCodeAndActiveTrue("MEMORIA_AUDITIVA")).isEmpty();
    }

    @Test
    void cadaEjercicioTieneNivelesDel1Al10ConParametros() {
        for (Exercise exercise : exercises.findAll()) {
            List<ExerciseLevel> exerciseLevels = levels.findByIdExerciseIdOrderByIdLevel(exercise.getId());
            assertThat(exerciseLevels).extracting(ExerciseLevel::getLevel)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(exerciseLevels).allSatisfy(level -> {
                assertThat(level.getParams()).isNotEmpty();
                assertThat(level.getEstimatedSeconds()).isPositive();
            });
        }
    }

    @Test
    void laDificultadCreceConElNivel() {
        Exercise reverse = exercises.findByCode("REVERSE_SEQUENCE").orElseThrow();
        List<ExerciseLevel> exerciseLevels = levels.findByIdExerciseIdOrderByIdLevel(reverse.getId());
        assertThat(exerciseLevels.get(0).getParams()).containsEntry("sequenceLength", 3);
        assertThat(exerciseLevels.get(9).getParams()).containsEntry("sequenceLength", 12);
    }

    @Test
    void soloLasMecanicasDeVelocidadTienenTiempoObjetivo() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DISTINCT e.type FROM exercise_levels l JOIN exercises e ON e.id = l.exercise_id
                WHERE l.target_response_ms IS NOT NULL""");
        assertThat(rows).extracting(r -> r.get("type"))
                .containsExactlyInAnyOrder("ODD_ONE_OUT", "STROOP", "N_BACK");
    }
}
