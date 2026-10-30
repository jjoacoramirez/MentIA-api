package com.mentia.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "training_profiles")
public class TrainingProfile implements Persistable<UUID> {

    public static final int DEFAULT_SESSION_MINUTES = 10;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "age_range", nullable = false)
    private AgeRange ageRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level")
    private EducationLevel educationLevel;

    @Column(name = "session_minutes", nullable = false)
    private short sessionMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goals", nullable = false)
    private List<String> goals = new ArrayList<>();

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    // Primera vez que se completó el onboarding: el evento se publica una sola vez aunque el flag cambie después.
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // El id lo asigna el registro (es el del usuario), así que hay que indicarle a Spring Data que es nuevo.
    @Transient
    private boolean isNew = true;

    protected TrainingProfile() {
    }

    public static TrainingProfile initial(UUID userId, AgeRange ageRange, Instant now) {
        TrainingProfile profile = new TrainingProfile();
        profile.userId = userId;
        profile.ageRange = ageRange;
        profile.sessionMinutes = DEFAULT_SESSION_MINUTES;
        profile.onboardingCompleted = false;
        profile.updatedAt = now;
        return profile;
    }

    public void changeAgeRange(AgeRange ageRange) {
        this.ageRange = ageRange;
    }

    /** {@code null} borra el nivel educativo. */
    public void changeEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
    }

    public void changeSessionMinutes(int sessionMinutes) {
        this.sessionMinutes = (short) sessionMinutes;
    }

    public void changeGoals(List<String> goals) {
        this.goals = new ArrayList<>(goals);
    }

    /**
     * @return {@code true} si es la primera vez que se completa el onboarding.
     */
    public boolean changeOnboardingCompleted(boolean completed, Instant now) {
        this.onboardingCompleted = completed;
        if (completed && onboardingCompletedAt == null) {
            onboardingCompletedAt = now;
            return true;
        }
        return false;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getUserId() {
        return userId;
    }

    public AgeRange getAgeRange() {
        return ageRange;
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public int getSessionMinutes() {
        return sessionMinutes;
    }

    public List<String> getGoals() {
        return List.copyOf(goals);
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
