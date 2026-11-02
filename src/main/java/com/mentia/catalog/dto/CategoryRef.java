package com.mentia.catalog.dto;

import java.util.UUID;

import com.mentia.catalog.domain.Category;

/** Referencia interna a una categoría: id para relacionar filas, code para mostrar y configurar. */
public record CategoryRef(UUID id, String code) {

    public static CategoryRef from(Category category) {
        return new CategoryRef(category.getId(), category.getCode());
    }
}
