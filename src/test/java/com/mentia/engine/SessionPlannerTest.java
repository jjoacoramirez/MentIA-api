package com.mentia.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mentia.engine.SessionPlanner.CategoryOption;
import com.mentia.engine.SessionPlanner.Choice;
import com.mentia.engine.SessionPlanner.ExerciseOption;

class SessionPlannerTest {

    private static final RotationProperties ROTATION = new RotationProperties(5, true, List.of(0.0, 0.25, 0.5, 0.75, 1.0));

    private final UUID attention = UUID.randomUUID();
    private final UUID visual = UUID.randomUUID();
    private final UUID working = UUID.randomUUID();
    private final ExerciseOption stroop = new ExerciseOption(UUID.randomUUID(), 60);
    private final ExerciseOption oddOne = new ExerciseOption(UUID.randomUUID(), 60);
    private final ExerciseOption grid = new ExerciseOption(UUID.randomUUID(), 60);
    private final ExerciseOption recognition = new ExerciseOption(UUID.randomUUID(), 60);
    private final ExerciseOption nBack = new ExerciseOption(UUID.randomUUID(), 60);
    private final ExerciseOption reverse = new ExerciseOption(UUID.randomUUID(), 60);

    private List<CategoryOption> categories(double attentionScore, double visualScore, double workingScore) {
        return List.of(
                new CategoryOption(attention, attentionScore, List.of(stroop, oddOne)),
                new CategoryOption(visual, visualScore, List.of(grid, recognition)),
                new CategoryOption(working, workingScore, List.of(nBack, reverse)));
    }

    private SessionPlanner planner(int maxItems, long seed) {
        Random random = new Random(seed);
        return new SessionPlanner(new SelectionProperties(1.0, maxItems), new RotationPolicy(ROTATION, random), random);
    }

    @Test
    void primeroUnItemPorCategoriaYDespuesLlenaElTiempo() {
        List<Choice> choices = planner(12, 1).plan(600, categories(50, 50, 50), Map.of());

        assertThat(choices).hasSize(10);
        assertThat(choices.subList(0, 3)).extracting(Choice::categoryId).containsExactly(attention, visual, working);
    }

    @Test
    void respetaElTopeDeItems() {
        assertThat(planner(4, 1).plan(3600, categories(50, 50, 50), Map.of())).hasSize(4);
    }

    @Test
    void siSoloEntraUnaCategoriaNoAgregaLasDemas() {
        assertThat(planner(12, 1).plan(60, categories(50, 50, 50), Map.of()))
                .extracting(Choice::categoryId).containsExactly(attention);
    }

    @Test
    void nuncaDevuelveUnaSesionVacia() {
        List<Choice> choices = planner(12, 1).plan(10, categories(50, 50, 50), Map.of());

        assertThat(choices).hasSize(1);
    }

    @Test
    void enLaMismaSesionNoRepiteEjercicioMientrasHayaAlternativas() {
        List<Choice> choices = planner(12, 7).plan(240, List.of(
                new CategoryOption(attention, 50, List.of(stroop, oddOne))), Map.of());

        assertThat(choices).hasSize(4);
        assertThat(choices.subList(0, 2)).extracting(Choice::exerciseId).doesNotHaveDuplicates();
    }

    @Test
    void evitaElEjercicioDeLaSesionAnterior() {
        for (long seed = 0; seed < 50; seed++) {
            List<Choice> choices = planner(12, seed).plan(60, List.of(
                    new CategoryOption(attention, 50, List.of(stroop, oddOne))), Map.of(stroop.exerciseId(), 1));
            assertThat(choices).extracting(Choice::exerciseId).containsExactly(oddOne.exerciseId());
        }
    }

    @Test
    void lasCategoriasDeMenorScoreRecibenMasItemsSinExcluirALasDemas() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (long seed = 0; seed < 300; seed++) {
            planner(12, seed).plan(600, categories(10, 90, 90), Map.of())
                    .forEach(c -> counts.merge(c.categoryId(), 1, Integer::sum));
        }

        assertThat(counts.get(attention)).isGreaterThan(counts.get(visual));
        assertThat(counts.get(visual)).isPositive();
        assertThat(counts.get(working)).isPositive();
    }

    @Test
    void pesoPorScore() {
        SessionPlanner planner = planner(12, 1);
        assertThat(planner.weight(new CategoryOption(attention, 0, List.of()))).isCloseTo(2.0, within(1e-9));
        assertThat(planner.weight(new CategoryOption(attention, 100, List.of()))).isCloseTo(1.0, within(1e-9));
        assertThat(planner.weight(new CategoryOption(attention, 60, List.of()))).isCloseTo(1.4, within(1e-9));
    }
}
