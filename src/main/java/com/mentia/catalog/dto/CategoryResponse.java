package com.mentia.catalog.dto;

import com.mentia.catalog.domain.Category;

public record CategoryResponse(String code, String name, String description, boolean mvp) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCode(), category.getName(), category.getDescription(), category.isMvp());
    }
}
