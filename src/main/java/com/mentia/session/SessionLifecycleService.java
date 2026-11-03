package com.mentia.session;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

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
 * Cierre de sesión. Versión mínima de BACK-07: consolida los intentos y, si es la evaluación inicial, fija el nivel
 * inicial por categoría. Para sesiones de entrenamiento los niveles no cambian todavía: las adaptaciones,
 * la comparación con la sesión anterior y la gamificación llegan con BACK-13.
 */
@Service
public class SessionLifecycleService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final PerformanceService performanceService;
    private final CatalogService catalogService;
    private final DifficultyEngine difficultyEngine;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionLifecycleService(SessionService sessionService, AttemptService attemptService,
            PerformanceService performanceService, CatalogService catalogService, DifficultyEngine difficultyEngine,
            ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock) {
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
        SessionResultResponse result = new SessionResultResponse(session.getId(), session.getType(),
                SessionStatus.COMPLETED, session.getItems().size(), outcomes.size(), correct,
                outcomes.size() - correct, meanAccuracy(outcomes),
                outcomes.stream().mapToLong(AttemptOutcome::responseTimeMs).sum(), categories, adaptations,
                null, null);

        session.complete(now, objectMapper.convertValue(result, MAP));
        if (assessment) {
            events.publishEvent(new AssessmentCompletedEvent(userId, sessionId, now));
        }
        return result;
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
