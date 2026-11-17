package com.mentia.feedback;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiResponse;
import com.mentia.ai.AiUnavailableException;
import com.mentia.ai.GuardedAiService;
import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.CategoryResponse;
import com.mentia.feedback.domain.SessionFeedback;
import com.mentia.feedback.dto.FeedbackResponse;
import com.mentia.session.SessionLifecycleService;

/**
 * Comentario de cierre de sesión. Se pide una vez que la sesión está completada (nunca mientras se entrena) y queda
 * guardado: reabrir el resultado no vuelve a llamar a la IA ni gasta la cuota de la persona.
 *
 * <p>Cualquier problema con la IA (apagada, sin cuota, caída, texto no apto) se resuelve con el texto básico: el
 * comentario siempre existe.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final SessionLifecycleService sessions;
    private final FeedbackRepository repository;
    private final GuardedAiService ai;
    private final CatalogService catalog;
    private final FeedbackProperties properties;
    private final Clock clock;

    public FeedbackService(SessionLifecycleService sessions, FeedbackRepository repository, GuardedAiService ai,
            CatalogService catalog, FeedbackProperties properties, Clock clock) {
        this.sessions = sessions;
        this.repository = repository;
        this.ai = ai;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sin transacción: mientras se espera a la IA no se mantiene abierta una conexión a la base. La sesión tiene que
     * ser de la persona y estar completada (404 / 409 si no).
     */
    public FeedbackResponse getOrCreate(UUID userId, UUID sessionId) {
        Map<String, Object> result = sessions.result(userId, sessionId);
        Optional<SessionFeedback> existing = repository.findById(sessionId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        FeedbackFacts facts = FeedbackFacts.from(result, areaNames());
        String text = null;
        FeedbackSource source = FeedbackSource.BASIC;
        try {
            AiResponse response = ai.generate(AiPurpose.USER_FEEDBACK, FeedbackText.request(facts, properties.maxTokens()),
                    userId);
            Optional<String> clean = FeedbackText.sanitize(response.text(), properties.maxChars(), properties.forbiddenTerms());
            if (clean.isPresent()) {
                text = clean.get();
                source = FeedbackSource.AI;
            } else {
                log.info("Comentario de la IA descartado por la validación: se usa el texto básico");
            }
        } catch (AiUnavailableException e) {
            log.debug("Comentario sin IA: {}", e.reason());
        }
        if (text == null) {
            text = FeedbackText.basic(facts);
        }

        try {
            return toResponse(repository.save(new SessionFeedback(sessionId, text, source, Instant.now(clock))));
        } catch (DataIntegrityViolationException e) {
            // Dos pedidos a la vez para la misma sesión: gana el primero.
            return repository.findById(sessionId).map(FeedbackService::toResponse).orElseThrow(() -> e);
        }
    }

    private Map<String, String> areaNames() {
        Map<String, String> names = new HashMap<>();
        for (CategoryResponse category : catalog.activeCategories()) {
            names.put(category.code(), category.name());
        }
        return names;
    }

    private static FeedbackResponse toResponse(SessionFeedback feedback) {
        return new FeedbackResponse(feedback.getText(), feedback.getSource());
    }
}
