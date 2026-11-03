package com.mentia.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.VariantSelector;
import com.mentia.catalog.dto.CategoryRef;
import com.mentia.catalog.dto.ExerciseDefinition;
import com.mentia.catalog.dto.LevelDefinition;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.engine.EngineProperties;
import com.mentia.engine.RotationProperties;
import com.mentia.engine.SessionPlanner;
import com.mentia.engine.SessionPlanner.CategoryOption;
import com.mentia.engine.SessionPlanner.Choice;
import com.mentia.engine.SessionPlanner.ExerciseOption;
import com.mentia.performance.PerformanceService;
import com.mentia.performance.dto.PerformanceSnapshot;
import com.mentia.profile.ProfileService;
import com.mentia.session.domain.PlannedItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.dto.SessionResponse;

/**
 * Generación de sesiones de entrenamiento (BACK-09): llena el tiempo pedido con ejercicios de las categorías
 * del MVP, priorizando las de menor desempeño, al nivel actual del usuario en cada una y con rotación de
 * ejercicios y variantes.
 */
@Service
public class SessionGenerationService {

    private final SessionService sessionService;
    private final SessionRepository repository;
    private final SessionHistoryService historyService;
    private final PerformanceService performanceService;
    private final ProfileService profileService;
    private final CatalogService catalogService;
    private final VariantSelector variantSelector;
    private final SessionPlanner planner;
    private final EngineProperties engine;
    private final RotationProperties rotation;
    private final RandomGenerator random;

    public SessionGenerationService(SessionService sessionService, SessionRepository repository,
            SessionHistoryService historyService, PerformanceService performanceService, ProfileService profileService,
            CatalogService catalogService, VariantSelector variantSelector, SessionPlanner planner,
            EngineProperties engine, RotationProperties rotation, RandomGenerator random) {
        this.sessionService = sessionService;
        this.repository = repository;
        this.historyService = historyService;
        this.performanceService = performanceService;
        this.profileService = profileService;
        this.catalogService = catalogService;
        this.variantSelector = variantSelector;
        this.planner = planner;
        this.engine = engine;
        this.rotation = rotation;
        this.random = random;
    }

    /**
     * Requiere un punto de partida (evaluación completada, o salteada por un admin). Si hay una sesión de
     * entrenamiento abierta y no vencida, la devuelve para retomarla.
     */
    @Transactional
    public Generated create(UUID userId, Integer durationMinutes) {
        if (!performanceService.hasInitialLevels(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ASSESSMENT_REQUIRED,
                    "Antes de entrenar necesitás completar la evaluación inicial.");
        }
        Optional<SessionResponse> open = sessionService.resumable(userId, SessionType.TRAINING);
        if (open.isPresent()) {
            return new Generated(open.get(), false);
        }

        int minutes = durationMinutes != null ? durationMinutes : profileService.sessionMinutes(userId);
        Map<UUID, PerformanceSnapshot> performance = performanceService.current(userId);
        Map<UUID, Integer> levelByCategory = new HashMap<>();
        Map<UUID, LevelDefinition> levelByExercise = new HashMap<>();
        Map<UUID, String> codeByCategory = new HashMap<>();
        List<CategoryOption> options = new ArrayList<>();

        for (CategoryRef category : catalogService.activeMvpCategories()) {
            PerformanceSnapshot snapshot = performance.get(category.id());
            int level = snapshot != null ? snapshot.level() : engine.initialLevel();
            levelByCategory.put(category.id(), level);
            codeByCategory.put(category.id(), category.code());
            List<ExerciseOption> exercises = new ArrayList<>();
            for (ExerciseDefinition exercise : catalogService.activeExercises(category.code())) {
                LevelDefinition definition = catalogService.level(exercise.id(), level);
                levelByExercise.put(exercise.id(), definition);
                exercises.add(new ExerciseOption(exercise.id(), definition.estimatedSeconds()));
            }
            if (!exercises.isEmpty()) {
                options.add(new CategoryOption(category.id(), snapshot != null ? snapshot.score() : 0.0, exercises));
            }
        }
        if (options.isEmpty()) {
            throw new IllegalStateException("No hay ejercicios activos en las categorías del MVP");
        }

        Map<UUID, Integer> exerciseUsage = historyService.recentUsage(userId, rotation.lookbackSessions())
                .exercisesSessionsAgo();
        List<Choice> choices = planner.plan(minutes * 60, options, exerciseUsage);

        List<PlannedItem> items = new ArrayList<>();
        Map<String, Integer> itemsByCategory = new LinkedHashMap<>();
        for (Choice choice : choices) {
            int level = levelByCategory.get(choice.categoryId());
            LevelDefinition definition = levelByExercise.get(choice.exerciseId());
            UUID variantId = variantSelector.pick(userId, choice.exerciseId(), level).id();
            items.add(new PlannedItem(choice.exerciseId(), variantId, choice.categoryId(), level, random.nextLong(),
                    definition.params(), definition.estimatedSeconds(), definition.targetResponseMs()));
            itemsByCategory.merge(codeByCategory.get(choice.categoryId()), 1, Integer::sum);
        }

        List<Map<String, Object>> rationale = RationaleBuilder.build(itemsByCategory, scoresByCode(performance,
                codeByCategory), namesByCode(), lastAdaptations(userId));
        return new Generated(sessionService.create(userId, SessionType.TRAINING, minutes, items, rationale), true);
    }

    private static Map<String, Double> scoresByCode(Map<UUID, PerformanceSnapshot> performance,
            Map<UUID, String> codeByCategory) {
        Map<String, Double> scores = new HashMap<>();
        codeByCategory.forEach((id, code) -> scores.put(code,
                performance.containsKey(id) ? performance.get(id).score() : 0.0));
        return scores;
    }

    private Map<String, String> namesByCode() {
        Map<String, String> names = new HashMap<>();
        catalogService.activeCategories().forEach(c -> names.put(c.code(), c.name()));
        return names;
    }

    /** Dirección del último ajuste de nivel por categoría, tomada del resultado de la última sesión completada. */
    @SuppressWarnings("unchecked")
    private Map<String, String> lastAdaptations(UUID userId) {
        Map<String, String> directions = new LinkedHashMap<>();
        repository.findFirstByUserIdAndTypeAndStatusOrderByFinishedAtDesc(userId, SessionType.TRAINING,
                        SessionStatus.COMPLETED)
                .map(s -> s.getResult() == null ? null : s.getResult().get("adaptations"))
                .filter(List.class::isInstance)
                .ifPresent(list -> ((List<Object>) list).stream()
                        .filter(Map.class::isInstance)
                        .map(a -> (Map<String, Object>) a)
                        .forEach(a -> directions.put(String.valueOf(a.get("category")),
                                String.valueOf(a.get("direction")))));
        return directions;
    }

    /** {@code created = false} cuando se devuelve una sesión abierta para retomarla. */
    public record Generated(SessionResponse session, boolean created) {
    }
}
