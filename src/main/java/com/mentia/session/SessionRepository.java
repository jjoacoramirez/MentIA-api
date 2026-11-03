package com.mentia.session;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.session.domain.TrainingSession;

public interface SessionRepository extends JpaRepository<TrainingSession, UUID> {
}
