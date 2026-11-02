package com.mentia.catalog;

import java.util.UUID;

import com.mentia.catalog.dto.VariantDefinition;

/**
 * Elige la variante de un ejercicio para un ítem de sesión. La implementación (en el módulo engine) aplica
 * la rotación según el uso reciente del usuario.
 */
public interface VariantSelector {

    VariantDefinition pick(UUID userId, UUID exerciseId, int level);
}
