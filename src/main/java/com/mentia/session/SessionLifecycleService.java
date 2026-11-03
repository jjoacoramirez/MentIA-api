package com.mentia.session;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.attempt.AttemptService;
import com.mentia.attempt.dto.AttemptOutcome;
import com.mentia.catalog.CatalogService;
import com.mentia.engine.DifficultyEngine;
import com.mentia.performance.PerformanceService;
import com.mentia.performance.dto.CategoryOutcome;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;
import com.mentia.session.dto.SessionResultResponse;
import com.mentia.session.dto.SessionResultResponse.Adaptation;
import com.mentia.session.dto.SessionResultResponse.CategoryResult;

/**
 * Cierre y abandono de sesiones (BACK-13). Al completar consolida los intentos, ajusta niveles (evaluación inicial:
 * nivel de partida; entrenamiento: motor adaptativo), actualiza el desempeño, compara con la sesión anterior,
 * guarda el snapshot del resultado y publica {@link SessionCompletedEvent}.
 */
@Service
public class SessionLifecycleService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final SessionRepository repository;
    private final ObjectProvider<SessionRewardsQuery> rewardsQuery;
    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final PerformanceService performanceService;
    private final CatalogService catalogService;
    private final DifficultyEngine difficultyEngine;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionLifecycleService(SessionRepository repository, ObjectProvider<SessionRewardsQuery> rewardsQuery,
            SessionService sessionService, AttemptService attemptService,
            PerformanceService performanceService, CatalogService catalogService, DifficultyEngine difficultyEngine,
            ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.rewardsQuery = rewardsQuery;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.performanceService = performanceService;
        this.catalogService = catalogService;
        this.difficultyEngine = difficultyEngine;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SessionResultResponse complete(UUID userId, UUID sessionId) {
        TrainingSession session = sessionService.load(userId, sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw SessionService.stateInvalid("Solo se puede cerrar una sesión en curso.");
        }
        Instant now = Instant.now(clock);
        List<AttemptOutcome> outcomes = attemptService.outcomes(sessionId);

        // Categorías en el orden en que aparecen en la sesión, con el nivel con el que se jugaron.
        Map<UUID, Integer> levelBefore = new LinkedHashMap<>();
        for (SessionItem item : session.getItems()) {
            levelBefore.putIfAbsent(item.getCategoryId(), item.getDifficultyLevel());
        }
        Map<UUID, List<AttemptOutcome>> byCategory = new LinkedHashMap<>();
        levelBefore.keySet().forEach(category -> byCategory.put(category, new ArrayList<>()));
        outcomes.forEach(o -> byCategory.computeIfAbsent(o.categoryId(), c -> new ArrayList<>()).add(o));

        boolean assessment = session.getType() == SessionType.INITIAL_ASSESSMENT;
        Map<UUID, Integer> levelAfter;
        if (assessment) {
            levelAfter = performanceService.applyInitialAssessment(userId, accuracies(byCategory));
        } else {
            levelAfter = adaptLevels(session, levelBefore, byCategory);
            Map<UUID, CategoryOutcome> sessionOutcomes = new LinkedHashMap<>();
            accuracies(byCategory).forEach((category, list) ->
                    sessionOutcomes.put(category, new CategoryOutcome(list, levelAfter.get(category))));
            performanceService.recordSession(userId, sessionOutcomes);
        }

        List<CategoryResult> categories = new ArrayList<>();
        List<Adaptation> adaptations = new ArrayList<>();
        byCategory.forEach((categoryId, categoryOutcomes) -> {
            String code = catalogService.categoryCode(categoryId);
            int before = levelBefore.getOrDefault(categoryId, levelAfter.get(categoryId));
            int after = levelAfter.getOrDefault(categoryId, before);
            categories.add(new CategoryResult(code, categoryOutcomes.size(), meanAccuracy(categoryOutcomes),
                    meanResponseTime(categoryOutcomes), before, after));
            adaptations.add(Adaptation.between(code, before, after));
        });

        int correct = (int) outcomes.stream().filter(AttemptOutcome::correct).count();
        double accuracy = meanAccuracy(outcomes);
        SessionResultResponse result = new SessionResultResponse(session.getId(), session.getType(),
                SessionStatus.COMPLETED, session.getItems().size(), outcomes.size(), correct,
                outcomes.size() - correct, accuracy,
                outcomes.stream().mapToLong(AttemptOutcome::responseTimeMs).sum(), categories, adaptations,
                comparison(userId, session.getType(), accuracy), null);

        // Primero se cierra la sesión y se avisa; después se leen las recompensas que dejaron los listeners.
        session.complete(now, objectMapper.convertValue(result, MAP));
        Set<String> practiced = new LinkedHashSet<>();
        categories.stream().filter(c -> c.itemsCompleted() > 0).forEach(c -> practiced.add(c.category()));
        events.publishEvent(new SessionCompletedEvent(userId, sessionId, session.getType(), outcomes.size(), accuracy,
                practiced, now));
        if (assessment) {
            events.publishEvent(new AssessmentCompletedEvent(userId, sessionId, now));
        }

        SessionRewardsQuery rewards = rewardsQuery.getIfAvailable();
        if (rewards != null) {
            Optional<SessionResultResponse.Rewards> earned = rewards.rewardsFor(sessionId);
            if (earned.isPresent()) {
                result = result.withGamification(earned.get());
                session.replaceResult(objectMapper.convertValue(result, MAP));
            }
        }
        return result;
    }

    /** Abandona una sesión abierta. No actualiza el desempeño; los intentos ya registrados quedan en el historial. */
    @Transactional
    public void abandon(UUID userId, UUID sessionId) {
        TrainingSession session = sessionService.load(userId, sessionId);
        if (!session.isOpen()) {
            throw SessionService.stateInvalid("Esta sesión ya está cerrada.");
        }
        Instant now = Instant.now(clock);
        session.abandon(now);
        events.publishEvent(new SessionAbandonedEvent(userId, sessionId, session.getType(), now));
    }

    /** El resultado guardado al completar la sesión. */
    @Transactional(readOnly = true)
    public Map<String, Object> result(UUID userId, UUID sessionId) {
        TrainingSession session = sessionService.load(userId, sessionId);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw SessionService.stateInvalid("La sesión todavía no tiene resultado: no está completada.");
        }
        return session.getResult();
    }

    /** Contra la última sesión completada del mismo tipo; null si es la primera. */
    private SessionResultResponse.Comparison comparison(UUID userId, SessionType type, double accuracy) {
        return repository.findFirstByUserIdAndTypeAndStatusOrderByFinishedAtDesc(userId, type, SessionStatus.COMPLETED)
                .map(TrainingSession::getResult)
                .map(previous -> previous.get("accuracy"))
                .filter(Number.class::isInstance)
                .map(previous -> {
                    double previousAccuracy = ((Number) previous).doubleValue();
                    return new SessionResultResponse.Comparison(previousAccuracy, accuracy - previousAccuracy);
                })
                .orElse(null);
    }

    /** Nivel con el que sigue cada categoría según el motor adaptativo (BACK-10). */
    private Map<UUID, Integer> adaptLevels(TrainingSession session, Map<UUID, Integer> levelBefore,
            Map<UUID, List<AttemptOutcome>> byCategory) {
        Map<UUID, Integer> targetByItem = new HashMap<>();
        session.getItems().forEach(item -> {
            if (item.getTargetResponseMs() != null) {
                targetByItem.put(item.getId(), item.getTargetResponseMs());
            }
        });
        Map<UUID, Integer> levels = new LinkedHashMap<>();
        byCategory.forEach((categoryId, list) -> {
            OptionalDouble timeRatio = list.stream()
                    .filter(o -> targetByItem.containsKey(o.sessionItemId()))
                    .mapToDouble(o -> (double) o.responseTimeMs() / targetByItem.get(o.sessionItemId()))
                    .average();
            DifficultyEngine.CategoryStats stats = new DifficultyEngine.CategoryStats(list.size(), meanAccuracy(list),
                    timeRatio.isPresent() ? timeRatio.getAsDouble() : null);
            levels.put(categoryId, difficultyEngine.nextLevel(levelBefore.get(categoryId), stats).level());
        });
        return levels;
    }

    private static Map<UUID, List<Double>> accuracies(Map<UUID, List<AttemptOutcome>> byCategory) {
        Map<UUID, List<Double>> accuracies = new LinkedHashMap<>();
        byCategory.forEach((category, list) -> accuracies.put(category,
                list.stream().map(AttemptOutcome::accuracy).toList()));
        return accuracies;
    }

    private static double meanAccuracy(List<AttemptOutcome> outcomes) {
        return outcomes.stream().mapToDouble(AttemptOutcome::accuracy).average().orElse(0.0);
    }

    private static Integer meanResponseTime(List<AttemptOutcome> outcomes) {
        return outcomes.isEmpty() ? null
                : (int) Math.round(outcomes.stream().mapToInt(AttemptOutcome::responseTimeMs).average().orElseThrow());
    }
}
