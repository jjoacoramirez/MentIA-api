package com.mentia.profile;

import com.mentia.catalog.CatalogService;
import com.mentia.common.error.FieldValidationException;
import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.TrainingProfile;
import com.mentia.profile.dto.ProfileResponse;
import com.mentia.profile.dto.ProfileUpdateRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de entrenamiento: rango etario, nivel educativo (el motor no lo usa), duración de sesión, objetivos y onboarding.
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final CatalogService catalogService;
    private final ObjectProvider<AssessmentCompletionQuery> assessmentCompletion;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProfileService(ProfileRepository profileRepository, CatalogService catalogService,
                          ObjectProvider<AssessmentCompletionQuery> assessmentCompletion,
                          ApplicationEventPublisher events, Clock clock) {
        this.profileRepository = profileRepository;
        this.catalogService = catalogService;
        this.assessmentCompletion = assessmentCompletion;
        this.events = events;
        this.clock = clock;
    }

    /** Se ejecuta dentro de la transacción del registro: si falla, no queda un usuario sin perfil. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createInitialProfile(UUID userId, AgeRange ageRange) {
        profileRepository.save(TrainingProfile.initial(userId, ageRange, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return toResponse(load(userId));
    }

    /** Duración de sesión elegida por el usuario: es el default al generar una sesión. */
    @Transactional(readOnly = true)
    public int sessionMinutes(UUID userId) {
        return load(userId).getSessionMinutes();
    }

    @Transactional
    public ProfileResponse update(UUID userId, ProfileUpdateRequest request) {
        TrainingProfile profile = load(userId);
        Instant now = Instant.now(clock);

        if (request.ageRange() != null) {
            profile.changeAgeRange(request.ageRange());
        }
        if (request.educationLevel() != null) {
            profile.changeEducationLevel(request.educationLevel().orElse(null));
        }
        if (request.sessionMinutes() != null) {
            profile.changeSessionMinutes(request.sessionMinutes());
        }
        if (request.goals() != null) {
            profile.changeGoals(validGoals(request.goals()));
        }
        if (request.onboardingCompleted() != null
                && profile.changeOnboardingCompleted(request.onboardingCompleted(), now)) {
            events.publishEvent(new OnboardingCompletedEvent(userId, now));
        }
        profile.touch(now);

        return toResponse(profile);
    }

    /** Sin repetidos, en el orden recibido, y solo códigos de categorías activas. */
    private List<String> validGoals(List<String> goals) {
        Set<String> active = catalogService.activeCategoryCodes();
        Set<String> unique = new LinkedHashSet<>(goals);
        for (String goal : unique) {
            if (!active.contains(goal)) {
                throw new FieldValidationException("goals", "Objetivo desconocido: " + goal);
            }
        }
        return List.copyOf(unique);
    }

    private TrainingProfile load(UUID userId) {
        // Todo usuario tiene perfil desde el registro; si falta, es un error de datos, no del cliente.
        return profileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario sin perfil de entrenamiento: " + userId));
    }

    private ProfileResponse toResponse(TrainingProfile profile) {
        AssessmentCompletionQuery query = assessmentCompletion.getIfAvailable();
        boolean assessmentCompleted = query != null && query.isCompleted(profile.getUserId());
        return ProfileResponse.from(profile, assessmentCompleted);
    }
}
