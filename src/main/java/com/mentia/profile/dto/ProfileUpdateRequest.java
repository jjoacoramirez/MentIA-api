package com.mentia.profile.dto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.EducationLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actualización parcial: un campo ausente no se modifica.
 * {@code educationLevel} es el único que se puede borrar: ausente = no cambia, {@code null} explícito = se borra.
 * <p>
 * Es una clase y no un record (excepción a la convención de DTOs) porque con un record Jackson no permite
 * distinguir un campo ausente de uno en {@code null}: acá el setter solo se invoca si el campo vino en el JSON.
 */
public final class ProfileUpdateRequest {

    private AgeRange ageRange;

    private EducationLevel educationLevel;
    private boolean educationLevelPresent;

    @Min(value = 5, message = "La duración de la sesión tiene que ser de al menos 5 minutos")
    @Max(value = 30, message = "La duración de la sesión puede ser de hasta 30 minutos")
    private Integer sessionMinutes;

    @Size(max = 4, message = "Podés elegir hasta 4 objetivos")
    private List<@NotBlank(message = "El objetivo no puede estar vacío") String> goals;

    private Boolean onboardingCompleted;

    public ProfileUpdateRequest() {
    }

    /** Para construirlo desde código; {@code educationLevel} sigue la misma convención que {@link #educationLevel()}. */
    public ProfileUpdateRequest(AgeRange ageRange, Optional<EducationLevel> educationLevel, Integer sessionMinutes,
            List<String> goals, Boolean onboardingCompleted) {
        this.ageRange = ageRange;
        if (educationLevel != null) {
            setEducationLevel(educationLevel.orElse(null));
        }
        this.sessionMinutes = sessionMinutes;
        this.goals = goals;
        this.onboardingCompleted = onboardingCompleted;
    }

    public AgeRange ageRange() {
        return ageRange;
    }

    /** {@code null} si no vino; {@code Optional.empty()} si vino en {@code null} (borrar). */
    public Optional<EducationLevel> educationLevel() {
        return educationLevelPresent ? Optional.ofNullable(educationLevel) : null;
    }

    public Integer sessionMinutes() {
        return sessionMinutes;
    }

    public List<String> goals() {
        return goals;
    }

    public Boolean onboardingCompleted() {
        return onboardingCompleted;
    }

    public void setAgeRange(AgeRange ageRange) {
        this.ageRange = ageRange;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
        this.educationLevelPresent = true;
    }

    public void setSessionMinutes(Integer sessionMinutes) {
        this.sessionMinutes = sessionMinutes;
    }

    public void setGoals(List<String> goals) {
        this.goals = goals;
    }

    public void setOnboardingCompleted(Boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProfileUpdateRequest other
                && Objects.equals(ageRange, other.ageRange)
                && Objects.equals(educationLevel(), other.educationLevel())
                && Objects.equals(sessionMinutes, other.sessionMinutes)
                && Objects.equals(goals, other.goals)
                && Objects.equals(onboardingCompleted, other.onboardingCompleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ageRange, educationLevel(), sessionMinutes, goals, onboardingCompleted);
    }

    @Override
    public String toString() {
        return "ProfileUpdateRequest[ageRange=" + ageRange + ", educationLevel=" + educationLevel()
                + ", sessionMinutes=" + sessionMinutes + ", goals=" + goals
                + ", onboardingCompleted=" + onboardingCompleted + "]";
    }
}
