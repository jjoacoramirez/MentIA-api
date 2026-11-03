package com.mentia.engine;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.VariantSelector;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.session.SessionHistoryService;
import com.mentia.session.dto.RecentUsage;

/**
 * Elige la variante de un ejercicio aplicando la rotación sobre el uso reciente del usuario.
 */
@Component
public class RotationVariantSelector implements VariantSelector {

    private final CatalogService catalogService;
    private final SessionHistoryService historyService;
    private final RotationPolicy policy;
    private final RotationProperties properties;

    public RotationVariantSelector(CatalogService catalogService, SessionHistoryService historyService,
            RotationPolicy policy, RotationProperties properties) {
        this.catalogService = catalogService;
        this.historyService = historyService;
        this.policy = policy;
        this.properties = properties;
    }

    @Override
    public VariantDefinition pick(UUID userId, UUID exerciseId, int level) {
        List<VariantDefinition> variants = catalogService.activeVariants(exerciseId);
        if (variants.isEmpty()) {
            // Un ejercicio activo sin variantes activas es un error de contenido, no del usuario.
            throw new IllegalStateException("El ejercicio " + exerciseId + " no tiene variantes activas");
        }
        RecentUsage usage = historyService.recentUsage(userId, properties.lookbackSessions());
        return policy.pick(variants, VariantDefinition::id, usage.variantsSessionsAgo());
    }
}
