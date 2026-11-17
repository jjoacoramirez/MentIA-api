package com.mentia.catalog.dto;

import java.util.List;

/**
 * Schema {@code GenerateContentResult}. {@code rejected} cuenta lo que la IA devolvió y la validación descartó.
 * {@code stoppedReason} es null si se completó todo; si no, por qué se cortó a la mitad.
 */
public record GenerateContentResponse(int requested, List<Created> created, int rejected, String stoppedReason) {

    public record Created(String exerciseCode, String variantKey, String theme, boolean active) {
    }
}
