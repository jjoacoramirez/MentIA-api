package com.mentia.feedback.dto;

import com.mentia.feedback.FeedbackSource;

/** Schema {@code SessionFeedback}: el comentario y quién lo escribió (para avisar si fue la IA). */
public record FeedbackResponse(String text, FeedbackSource source) {
}
