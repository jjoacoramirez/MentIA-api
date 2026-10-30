package com.mentia.common.error;

/**
 * Validación de negocio sobre un campo de entrada que Bean Validation no puede resolver sola
 * (por ejemplo, que un código exista en el catálogo). Se responde como 400 VALIDATION_ERROR con {@code errors}.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
