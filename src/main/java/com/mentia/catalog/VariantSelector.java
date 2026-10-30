package com.mentia.catalog;

import java.util.UUID;

import com.mentia.catalog.dto.VariantDefinition;

/**
 * Elige la variante de un ejercicio para un ítem de sesión. BACK-11 la implementa con rotación según el uso
 * reciente del usuario; hasta entonces la elección es aleatoria ({@link RandomVariantSelector}).
 */
public interface VariantSelector {

    VariantDefinition pick(UUID userId, UUID exerciseId, int level);
}
