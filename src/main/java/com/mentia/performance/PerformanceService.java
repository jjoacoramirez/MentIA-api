package com.mentia.performance;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.engine.EngineProperties;
import com.mentia.performance.domain.PerformanceProfile;

/**
 * Perfil de desempeño por categoría. En BACK-07 fija el punto de partida; BACK-08 suma la actualización
 * después de cada sesión y la consulta.
 */
@Service
public class PerformanceService {

    private final PerformanceRepository repository;
    private final InitialLevelPolicy initialLevelPolicy;
    private final EngineProperties engine;
    private final Clock clock;

    public PerformanceService(PerformanceRepository repository, InitialLevelPolicy initialLevelPolicy,
            EngineProperties engine, Clock clock) {
        this.repository = repository;
        this.initialLevelPolicy = initialLevelPolicy;
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
