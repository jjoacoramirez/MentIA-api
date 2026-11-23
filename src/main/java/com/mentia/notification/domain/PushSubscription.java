package com.mentia.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_reminded_on")
    private LocalDate lastRemindedOn;

    @Column(name = "p256dh", length = 128)
    private String p256dh;

    @Column(name = "auth_secret", length = 64)
    private String authSecret;

    public String getP256dh() { return p256dh; }
    public String getAuthSecret() { return authSecret; }
    public void updateKeys(String p256dh, String authSecret) {
        this.p256dh = p256dh;
        this.authSecret = authSecret;
    }

    protected PushSubscription() {
    }

    public PushSubscription(UUID id, UUID userId, String endpoint, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.endpoint = endpoint;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getLastRemindedOn() {
        return lastRemindedOn;
    }

    /** Un mismo navegador puede pasar de una cuenta a otra: la suscripción sigue a quien la registró último. */
    public void assignTo(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.lastRemindedOn = null;
    }

    public void markReminded(LocalDate day) {
        this.lastRemindedOn = day;
    }

    /** No se imprime el endpoint: identifica un dispositivo. */
    @Override
    public String toString() {
        return "PushSubscription[id=" + id + "]";
    }
}
