package com.mentia.performance;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.CategoryRef;
import com.mentia.engine.EngineProperties;
import com.mentia.performance.domain.PerformanceProfile;
import com.mentia.performance.dto.CategoryOutcome;
import com.mentia.performance.dto.PerformanceProfileResponse;
import com.mentia.performance.dto.PerformanceSnapshot;

/**
 * Perfil de desempeño por categoría. En BACK-07 fija el punto de partida; BACK-08 suma la actualización
 * después de cada sesión y la consulta.
 */
@Service
public class PerformanceService {

    private final PerformanceRepository repository;
    private final InitialLevelPolicy initialLevelPolicy;
    private final CatalogService catalogService;
    private final EngineProperties engine;
    private final Clock clock;

    public PerformanceService(PerformanceRepository repository, InitialLevelPolicy initialLevelPolicy,
            CatalogService catalogService, EngineProperties engine, Clock clock) {
        this.repository = repository;
        this.initialLevelPolicy = initialLevelPolicy;
        this.catalogService = catalogService;
        this.engine = engine;
        this.clock = clock;
    }

    /**
     * Nivel y score iniciales a partir de las precisiones de la evaluación. Una categoría sin intentos
     * se toma como precisión 0. Reemplaza cualquier punto de partida anterior (por ejemplo, si se repite).
     *
     * @return nivel inicial por categoría
     */
    @Transactional
    public Map<UUID, Integer> applyInitialAssessment(UUID userId, Map<UUID, List<Double>> accuraciesByCategory) {
        Instant now = Instant.now(clock);
        Map<UUID, Integer> levels = new LinkedHashMap<>();
        accuraciesByCategory.forEach((categoryId, accuracies) -> {
            double accuracy = accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            int level = initialLevelPolicy.levelFor(accuracy);
            setStartingPoint(userId, categoryId, initialLevelPolicy.scoreFor(accuracy), level, accuracies.size(), now);
            levels.put(categoryId, level);
        });
        return levels;
    }

    /** Punto de partida sin evaluación (herramienta de debug para admins): nivel mínimo y score 0. */
    @Transactional
    public void applyDefaultLevels(UUID userId, Collection<UUID> categoryIds) {
        Instant now = Instant.now(clock);
        categoryIds.forEach(categoryId -> setStartingPoint(userId, categoryId, 0.0, engine.levelMin(), 0, now));
    }

    /**
     * Actualiza score (media móvil con {@code ema-alpha}) y nivel de cada categoría después de una sesión.
     * Una categoría sin intentos no cambia. Si todavía no tenía perfil (por ejemplo, una categoría agregada
     * después de la evaluación), arranca con el score de esta sesión.
     */
    @Transactional
    public void recordSession(UUID userId, Map<UUID, CategoryOutcome> outcomes) {
        Instant now = Instant.now(clock);
        outcomes.forEach((categoryId, outcome) -> {
            if (outcome.accuracies().isEmpty()) {
                return;
            }
            double sessionScore = initialLevelPolicy.scoreFor(
                    outcome.accuracies().stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            int level = engine.clamp(outcome.newLevel());
            repository.findById(new PerformanceProfile.Key(userId, categoryId)).ifPresentOrElse(
                    profile -> profile.recordSession(sessionScore, outcome.accuracies().size(), level,
                            engine.emaAlpha(), now),
                    () -> repository.save(new PerformanceProfile(userId, categoryId, sessionScore, level,
                            outcome.accuracies().size(), now)));
        });
    }

    /** Score y nivel actuales por categoría. */
    @Transactional(readOnly = true)
    public Map<UUID, PerformanceSnapshot> current(UUID userId) {
        Map<UUID, PerformanceSnapshot> snapshots = new LinkedHashMap<>();
        repository.findByIdUserId(userId).forEach(p -> snapshots.put(p.getCategoryId(), PerformanceSnapshot.from(p)));
        return snapshots;
    }

    /**
     * Vista para {@code GET /profile/performance}, en el orden de las categorías activas. El score se redondea
     * a un decimal: es una referencia de desempeño dentro de MentIA, no una medición.
     */
    @Transactional(readOnly = true)
    public PerformanceProfileResponse profile(UUID userId) {
        Map<UUID, PerformanceSnapshot> current = current(userId);
        List<PerformanceProfileResponse.Entry> entries = new ArrayList<>();
        for (CategoryRef category : catalogService.activeCategoryRefs()) {
            PerformanceSnapshot snapshot = current.get(category.id());
            if (snapshot != null) {
                entries.add(new PerformanceProfileResponse.Entry(category.code(),
                        Math.round(snapshot.score() * 10) / 10.0, snapshot.level(), snapshot.updatedAt()));
            }
        }
        return new PerformanceProfileResponse(entries);
    }

    /** Si el usuario ya tiene un punto de partida (evaluación completada o salteada por un admin). */
    @Transactional(readOnly = true)
    public boolean hasInitialLevels(UUID userId) {
        return repository.existsByIdUserId(userId);
    }

    private void setStartingPoint(UUID userId, UUID categoryId, double score, int level, int samples, Instant now) {
        repository.findById(new PerformanceProfile.Key(userId, categoryId)).ifPresentOrElse(
                existing -> existing.reset(score, level, samples, now),
                () -> repository.save(new PerformanceProfile(userId, categoryId, score, level, samples, now)));
    }
}
