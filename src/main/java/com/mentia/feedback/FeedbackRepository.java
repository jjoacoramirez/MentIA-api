package com.mentia.feedback;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mentia.feedback.domain.SessionFeedback;

interface FeedbackRepository extends JpaRepository<SessionFeedback, UUID> {
}
