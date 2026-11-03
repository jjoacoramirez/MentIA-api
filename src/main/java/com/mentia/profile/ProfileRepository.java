package com.mentia.profile;

import com.mentia.profile.domain.TrainingProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<TrainingProfile, UUID> {
}
