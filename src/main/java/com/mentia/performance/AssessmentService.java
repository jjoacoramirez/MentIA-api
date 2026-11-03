package com.mentia.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.CategoryRef;
import com.mentia.catalog.dto.ExerciseDefinition;
import com.mentia.catalog.dto.LevelDefinition;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.engine.EngineProperties;
import com.mentia.session.SessionService;
import com.mentia.session.domain.PlannedItem;
import com.mentia.session.domain.SessionType;
import com.mentia.session.dto.SessionResponse;

/**
 * Evaluación inicial (BACK-07): {@code exercises-per-category} ejercicios por cada categoría activa del MVP,
 * todos al nivel {@code initial-level}, sin rotación ni rationale. Usa siempre la primera variante activa de
 * cada ejercicio para que la evaluación sea comparable entre usuarios y entre repeticiones.
 * El nivel inicial se calcula al cerrar la sesión ({@code SessionLifecycleService}).
 */
@Service
public class AssessmentService {

    private final SessionService sessionService;
    private final CatalogService catalogService;
    private final PerformanceService performanceService;
    private final AssessmentProperties assessment;
    private final EngineProperties engine;
    private final RandomGenerator random;

    public AssessmentService(SessionService sessionService, CatalogService catalogService,
            PerformanceService performanceService, AssessmentProperties assessment, EngineProperties engine,
            RandomGenerator random) {
        this.sessionService = sessionService;
        this.catalogService = catalogService;
        this.performanceService = performanceService;
        this.assessment = assessment;
        this.engine = engine;
        this.random = random;
    }

    /**
     * Si hay una evaluación abierta (y no vencida) la devuelve para retomarla, salvo con {@code restart},
     * que la abandona y crea una nueva.
     */
    @Transactional
    public Started start(UUID userId, boolean restart) {
        if (restart) {
            sessionService.abandonOpen(userId, SessionType.INITIAL_ASSESSMENT);
        } else {
            Optional<SessionResponse> open = sessionService.resumable(userId, SessionType.INITIAL_ASSESSMENT);
            if (open.isPresent()) {
                return new Started(open.get(), false);
            }
        }

        List<PlannedItem> items = plannedItems();
        int totalSeconds = items.stream().mapToInt(PlannedItem::estimatedSeconds).sum();
        int plannedMinutes = Math.max(1, (int) Math.ceil(totalSeconds / 60.0));
        SessionResponse session = sessionService.create(userId, SessionType.INITIAL_ASSESSMENT, plannedMinutes,
                items, List.of());
        return new Started(session, true);
    }

    /**
     * Solo para admins (debug): deja nivel mínimo en todas las categorías del MVP sin hacer la evaluación.
     * No la marca como completada.
     */
    @Transactional
    public void skip(UUID userId) {
        sessionService.abandonOpen(userId, SessionType.INITIAL_ASSESSMENT);
        performanceService.applyDefaultLevels(userId,
                catalogService.activeMvpCategories().stream().map(CategoryRef::id).toList());
    }

    private List<PlannedItem> plannedItems() {
        List<PlannedItem> items = new ArrayList<>();
        for (CategoryRef category : catalogService.activeMvpCategories()) {
            catalogService.activeExercises(category.code()).stream()
                    .limit(assessment.exercisesPerCategory())
                    .map(this::planned)
                    .forEach(items::add);
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("No hay ejercicios activos en las categorías del MVP");
        }
        return items;
    }

    private PlannedItem planned(ExerciseDefinition exercise) {
        List<VariantDefinition> variants = catalogService.activeVariants(exercise.id());
        if (variants.isEmpty()) {
            throw new IllegalStateException("El ejercicio " + exercise.code() + " no tiene variantes activas");
        }
        LevelDefinition level = catalogService.level(exercise.id(), engine.initialLevel());
        return new PlannedItem(exercise.id(), variants.getFirst().id(), exercise.categoryId(), level.level(),
                random.nextLong(), level.params(), level.estimatedSeconds(), level.targetResponseMs());
    }

    /** {@code created = false} cuando se devuelve una evaluación existente para retomarla. */
    public record Started(SessionResponse session, boolean created) {
    }
}
