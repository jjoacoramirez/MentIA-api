package com.mentia.feedback.domain;

import java.time.Instant;
import java.util.UUID;

import com.mentia.feedback.FeedbackSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** El comentario de una sesión: uno solo, para no volver a llamar a la IA al reabrir el resultado. */
@Entity
@Table(name = "session_feedback")
public class SessionFeedback {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "text", nullable = false, length = 600)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private FeedbackSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SessionFeedback() {
    }

    public SessionFeedback(UUID sessionId, String text, FeedbackSource source, Instant createdAt) {
        this.sessionId = sessionId;
        this.text = text;
        this.source = source;
        this.createdAt = createdAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getText() {
        return text;
    }

    public FeedbackSource getSource() {
        return source;
    }
}
