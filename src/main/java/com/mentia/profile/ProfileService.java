package com.mentia.profile;

import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.TrainingProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de entrenamiento. En BACK-01 solo se crea el perfil inicial; GET/PUT llegan con BACK-02.
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final Clock clock;

    public ProfileService(ProfileRepository profileRepository, Clock clock) {
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    /** Se ejecuta dentro de la transacción del registro: si falla, no queda un usuario sin perfil. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createInitialProfile(UUID userId, AgeRange ageRange) {
        profileRepository.save(TrainingProfile.initial(userId, ageRange, Instant.now(clock)));
    }
}
