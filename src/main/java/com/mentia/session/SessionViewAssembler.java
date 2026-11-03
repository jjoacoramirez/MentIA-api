package com.mentia.session;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.ExerciseDefinition;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.TrainingSession;
import com.mentia.session.dto.SessionResponse;

/**
 * Arma la vista de una sesión para el cliente: completa cada ítem con el ejercicio y la variante del catálogo.
 */
@Component
public class SessionViewAssembler {

    private final CatalogService catalogService;

    public SessionViewAssembler(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public SessionResponse toResponse(TrainingSession session) {
        List<SessionResponse.Item> items = session.getItems().stream().map(this::toItem).toList();
        int estimatedSeconds = session.getItems().stream().mapToInt(SessionItem::getEstimatedSeconds).sum();
        return new SessionResponse(session.getId(), session.getType(), session.getStatus(), session.getPlannedMinutes(),
                estimatedSeconds, items, session.getRationale(), session.getCreatedAt());
    }

    private SessionResponse.Item toItem(SessionItem item) {
        ExerciseDefinition exercise = catalogService.exercise(item.getExerciseId());
        VariantDefinition variant = catalogService.variant(item.getVariantId());
        return new SessionResponse.Item(item.getId(), item.getPosition(), exercise.code(), exercise.type(),
                exercise.categoryCode(), exercise.modality(), item.getDifficultyLevel(), variant.variantKey(),
                item.getSeed(), item.getParams(), variant.content(), exercise.instructions());
    }
}
