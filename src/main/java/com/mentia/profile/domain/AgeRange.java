package com.mentia.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Rango etario. Se usa en lugar de la edad exacta por minimización de datos.
 */
public enum AgeRange {

    FROM_18_TO_24("18-24"),
    FROM_25_TO_30("25-30"),
    FROM_31_TO_40("31-40"),
    FROM_41("41+");

    private final String value;

    AgeRange(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AgeRange fromValue(String value) {
        return Arrays.stream(values())
                .filter(r -> r.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rango etario no válido"));
    }
}
