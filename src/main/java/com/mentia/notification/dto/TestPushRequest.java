package com.mentia.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Schema {@code TestPushRequest}: el alias de la cuenta a la que se le manda el aviso de prueba. */
public record TestPushRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_.]*$", message = "El alias tiene caracteres no válidos")
        String username) {
}
