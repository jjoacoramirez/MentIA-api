package com.mentia.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ExerciseType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private Modality modality;

    @Column(name = "instructions", nullable = false, columnDefinition = "text")
    private String instructions;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "post_mvp", nullable = false)
    private boolean postMvp;

    protected Exercise() {
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

    public ExerciseType getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public Modality getModality() {
        return modality;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPostMvp() {
        return postMvp;
    }
}
