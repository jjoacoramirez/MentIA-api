package com.mentia.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Usuario seudonimizado: no tiene nombre real, email ni ningún otro dato personal.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "recovery_code_hash")
    private String recoveryCodeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "recovery_failed_attempts", nullable = false)
    private int recoveryFailedAttempts;

    @Column(name = "recovery_locked_until")
    private Instant recoveryLockedUntil;

    protected User() {
    }

    public User(String username, String passwordHash, String recoveryCodeHash, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.recoveryCodeHash = recoveryCodeHash;
        this.createdAt = createdAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeRecoveryCode(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
    }

    public boolean isRecoveryLocked(Instant now) {
        return recoveryLockedUntil != null && now.isBefore(recoveryLockedUntil);
    }

    /** Al llegar a {@code maxAttempts} fallidos seguidos, bloquea la recuperación durante {@code lockout}. */
    public void registerFailedRecovery(Instant now, int maxAttempts, Duration lockout) {
        recoveryFailedAttempts++;
        if (recoveryFailedAttempts >= maxAttempts) {
            recoveryLockedUntil = now.plus(lockout);
            recoveryFailedAttempts = 0;
        }
    }

    public void clearFailedRecoveries() {
        recoveryFailedAttempts = 0;
        recoveryLockedUntil = null;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRecoveryCodeHash() {
        return recoveryCodeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
