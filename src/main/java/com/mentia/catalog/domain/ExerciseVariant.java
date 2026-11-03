package com.mentia.catalog.domain;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Conjunto de estímulos de una mecánica (tema de imágenes, alfabeto, paleta de colores).
 * La forma de {@code content} depende del tipo de ejercicio.
 */
@Entity
@Table(name = "exercise_variants")
public class ExerciseVariant {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "variant_key", nullable = false)
    private String variantKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private Map<String, Object> content;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ExerciseVariant() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public Map<String, Object> getContent() {
        return Map.copyOf(content);
    }

    public boolean isActive() {
        return active;
    }
}
