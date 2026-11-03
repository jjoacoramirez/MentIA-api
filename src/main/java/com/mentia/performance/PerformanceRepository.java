package com.mentia.performance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.performance.domain.PerformanceProfile;

public interface PerformanceRepository extends JpaRepository<PerformanceProfile, PerformanceProfile.Key> {

    List<PerformanceProfile> findByIdUserId(UUID userId);

    boolean existsByIdUserId(UUID userId);
}
