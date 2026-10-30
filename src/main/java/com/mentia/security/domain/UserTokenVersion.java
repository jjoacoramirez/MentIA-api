package com.mentia.security.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Versión vigente de los tokens de un usuario. Un token con una versión menor ya no es válido.
 * Si no hay fila, la versión es 0.
 */
@Entity
@Table(name = "user_token_versions")
public class UserTokenVersion {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "version", nullable = false)
    private int version;

    protected UserTokenVersion() {
    }

    public UUID getUserId() {
        return userId;
    }

    public int getVersion() {
        return version;
    }
}
