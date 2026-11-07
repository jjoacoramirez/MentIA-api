package com.mentia.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Decide qué ejercicios lleva una sesión de entrenamiento (docs/plan-backend.md, sección 7):
 * <ol>
 *   <li>Un ítem por categoría, en el orden recibido, si entra en el tiempo.</li>
 *   <li>El resto por sorteo ponderado: las categorías con menor score pesan más, pero ninguna queda excluida.</li>
 *   <li>Dentro de cada categoría, el ejercicio se elige con la {@link RotationPolicy} entre los que todavía no están
 *       en la sesión: un ejercicio nunca se repite dentro de la misma sesión.</li>
 * </ol>
 * Se agrega mientras quede tiempo, queden ejercicios sin usar y no se llegue a {@code max-items}: si se acaban los
 * ejercicios, la sesión dura menos que lo pedido. Nunca devuelve una sesión vacía:
 * si nada entra en el tiempo, lleva el ejercicio más corto.
 */
public class SessionPlanner {

    private final SelectionProperties properties;
    private final RotationPolicy rotation;
    private final RandomGenerator random;

    public SessionPlanner(SelectionProperties properties, RotationPolicy rotation, RandomGenerator random) {
        this.properties = properties;
        this.rotation = rotation;
        this.random = random;
    }

    /**
     * @param exerciseSessionsAgo uso reciente de cada ejercicio (ver {@link RotationPolicy#pick})
     */
    public List<Choice> plan(int budgetSeconds, List<CategoryOption> categories, Map<UUID, Integer> exerciseSessionsAgo) {
        List<Choice> choices = new ArrayList<>();
        int remaining = budgetSeconds;

        for (CategoryOption category : categories) {
            if (choices.size() >= properties.maxItems()) {
                break;
            }
            ExerciseOption exercise = pickExercise(category, remaining, exerciseSessionsAgo, choices);
            if (exercise != null) {
                remaining -= add(choices, category, exercise);
            }
        }

        while (choices.size() < properties.maxItems()) {
            final int left = remaining;
            List<CategoryOption> fitting = categories.stream()
                    .filter(c -> c.exercises().stream()
                            .anyMatch(e -> e.estimatedSeconds() <= left && !inSession(choices, e)))
                    .toList();
            if (fitting.isEmpty()) {
                break;
            }
            CategoryOption category = weightedCategory(fitting);
            remaining -= add(choices, category, pickExercise(category, remaining, exerciseSessionsAgo, choices));
        }

        if (choices.isEmpty()) {
            shortest(categories).ifPresent(pair -> choices.add(new Choice(pair.category().categoryId(),
                    pair.exercise().exerciseId())));
        }
        return choices;
    }

    double weight(CategoryOption category) {
        double score = Math.max(0, Math.min(100, category.score()));
        return 1 + properties.weakCategoryBoost() * (100 - score) / 100;
    }

    private ExerciseOption pickExercise(CategoryOption category, int remaining, Map<UUID, Integer> usage,
            List<Choice> choices) {
        List<ExerciseOption> fitting = category.exercises().stream()
                .filter(e -> e.estimatedSeconds() <= remaining && !inSession(choices, e))
                .toList();
        return fitting.isEmpty() ? null : rotation.pick(fitting, ExerciseOption::exerciseId, usage);
    }

    private static boolean inSession(List<Choice> choices, ExerciseOption exercise) {
        return choices.stream().anyMatch(c -> c.exerciseId().equals(exercise.exerciseId()));
    }

    private CategoryOption weightedCategory(List<CategoryOption> categories) {
        double total = categories.stream().mapToDouble(this::weight).sum();
        double target = random.nextDouble() * total;
        for (CategoryOption category : categories) {
            target -= weight(category);
            if (target < 0) {
                return category;
            }
        }
        return categories.getLast();
    }

    private static int add(List<Choice> choices, CategoryOption category, ExerciseOption exercise) {
        choices.add(new Choice(category.categoryId(), exercise.exerciseId()));
        return exercise.estimatedSeconds();
    }

    private static Optional<Pair> shortest(List<CategoryOption> categories) {
        return categories.stream()
                .flatMap(c -> c.exercises().stream().map(e -> new Pair(c, e)))
                .min(Comparator.comparingInt(p -> p.exercise().estimatedSeconds()));
    }

    private record Pair(CategoryOption category, ExerciseOption exercise) {
    }

    public record CategoryOption(UUID categoryId, double score, List<ExerciseOption> exercises) {
    }

    /** {@code estimatedSeconds} del ejercicio en el nivel con el que se va a jugar. */
    public record ExerciseOption(UUID exerciseId, int estimatedSeconds) {
    }

    public record Choice(UUID categoryId, UUID exerciseId) {
    }
}
