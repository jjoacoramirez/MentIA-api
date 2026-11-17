package com.mentia.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.common.error.FieldValidationException;
import com.mentia.notification.domain.PushSubscription;
import com.mentia.notification.dto.PushConfigResponse;

@Service
public class NotificationService {

    /** Lo que necesita el job para mandar un aviso, sin exponer la entidad. */
    record Due(UUID subscriptionId, UUID userId, String endpoint) {
        @Override
        public String toString() {
            return "Due[subscriptionId=" + subscriptionId + "]";
        }
    }

    private final PushSubscriptionRepository repository;
    private final NotificationProperties properties;
    private final PushEndpointPolicy policy;
    private final Clock clock;

    public NotificationService(PushSubscriptionRepository repository, NotificationProperties properties,
            PushEndpointPolicy policy, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.policy = policy;
        this.clock = clock;
    }

    public PushConfigResponse config() {
        return properties.enabled() ? new PushConfigResponse(true, properties.vapidPublicKey())
                : new PushConfigResponse(false, null);
    }

    /** Idempotente: registrar dos veces el mismo navegador deja una sola suscripción. */
    @Transactional
    public void subscribe(UUID userId, String endpoint) {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PUSH_DISABLED,
                    "Los recordatorios no están disponibles por el momento.");
        }
        if (!policy.isAllowed(endpoint)) {
            throw new FieldValidationException("endpoint", "La suscripción no es válida");
        }
        Instant now = Instant.now(clock);
        repository.findByEndpoint(endpoint).ifPresentOrElse(existing -> {
            if (!existing.getUserId().equals(userId)) {
                existing.assignTo(userId, now);
            }
        }, () -> repository.save(new PushSubscription(UUID.randomUUID(), userId, endpoint, now)));
        repository.flush();
        // Tope por usuario: se descartan las más viejas.
        List<PushSubscription> mine = repository.findByUserIdOrderByCreatedAtAsc(userId);
        int excess = mine.size() - properties.maxSubscriptionsPerUser();
        if (excess > 0) {
            repository.deleteAll(mine.subList(0, excess));
        }
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.deleteByUserAndEndpoint(userId, endpoint);
    }

    @Transactional(readOnly = true)
    List<Due> due(LocalDate today) {
        return repository.findDue(today).stream()
                .map(s -> new Due(s.getId(), s.getUserId(), s.getEndpoint())).toList();
    }

    @Transactional
    void markReminded(UUID subscriptionId, LocalDate today) {
        repository.findById(subscriptionId).ifPresent(s -> s.markReminded(today));
    }

    @Transactional
    void remove(UUID subscriptionId) {
        repository.deleteById(subscriptionId);
    }
}
