package com.mentia.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoría de entrenamiento. El código es un dato, no un enum: una categoría nueva es una fila nueva.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "mvp", nullable = false)
    private boolean mvp;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Category() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isMvp() {
        return mvp;
    }

    public boolean isActive() {
        return active;
    }
}
