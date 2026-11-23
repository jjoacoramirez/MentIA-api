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
    record Due(UUID subscriptionId, UUID userId, String endpoint, String p256dh, String auth) {
        Due(UUID subscriptionId, UUID userId, String endpoint) { this(subscriptionId, userId, endpoint, null, null); }
        @Override
        public String toString() {
            return "Due[subscriptionId=" + subscriptionId + "]";
        }
    }

    /** Dispositivo de una cuenta. No imprime la URL: identifica un dispositivo. */
    record Device(UUID subscriptionId, String endpoint, String p256dh, String auth) {
        Device(UUID subscriptionId, String endpoint) { this(subscriptionId, endpoint, null, null); }
        @Override
        public String toString() {
            return "Device[subscriptionId=" + subscriptionId + "]";
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
        subscribe(userId, endpoint, null, null);
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PUSH_DISABLED,
                    "Los recordatorios no están disponibles por el momento.");
        }
        if (!policy.isAllowed(endpoint)) {
            throw new FieldValidationException("endpoint", "La suscripción no es válida");
        }
        if (p256dh != null || auth != null) {
            try {
                if (p256dh == null || auth == null) throw new IllegalArgumentException();
                WebPushEncryption.validate(p256dh, auth);
            } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
                throw new FieldValidationException("p256dh", "Las claves de la suscripción no son válidas");
            }
        }
        Instant now = Instant.now(clock);
        repository.findByEndpoint(endpoint).ifPresentOrElse(existing -> {
            if (!existing.getUserId().equals(userId)) {
                existing.assignTo(userId, now);
            }
        }, () -> repository.save(new PushSubscription(UUID.randomUUID(), userId, endpoint, now)));
        repository.flush();
        if (p256dh != null) repository.findByEndpoint(endpoint).ifPresent(existing -> existing.updateKeys(p256dh, auth));
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
                .map(s -> new Due(s.getId(), s.getUserId(), s.getEndpoint(), s.getP256dh(), s.getAuthSecret())).toList();
    }

    @Transactional(readOnly = true)
    List<Device> devicesOf(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(s -> new Device(s.getId(), s.getEndpoint(), s.getP256dh(), s.getAuthSecret())).toList();
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
