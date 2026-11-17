package com.mentia.session;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.profile.AssessmentCompletionQuery;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;

/**
 * {@code assessmentCompleted} del perfil: true si el usuario completó una evaluación inicial.
 * Saltearla (solo admins) no cuenta como completarla.
 */
@Component
public class AssessmentCompletion implements AssessmentCompletionQuery {

    private final SessionRepository repository;

    public AssessmentCompletion(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompleted(UUID userId) {
        return repository.existsByUserIdAndTypeAndStatus(userId, SessionType.INITIAL_ASSESSMENT,
                SessionStatus.COMPLETED);
    }
}
