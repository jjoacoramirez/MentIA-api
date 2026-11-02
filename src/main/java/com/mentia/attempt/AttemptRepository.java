package com.mentia.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.attempt.domain.Attempt;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    Optional<Attempt> findByUserIdAndClientAttemptId(UUID userId, UUID clientAttemptId);

    boolean existsBySessionItemId(UUID sessionItemId);

    List<Attempt> findBySessionId(UUID sessionId);
}
