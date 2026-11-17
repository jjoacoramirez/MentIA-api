package com.mentia.auth.dto;

import com.mentia.common.validation.MaxUtf8Bytes;
import com.mentia.profile.domain.AgeRange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El username se acepta en mayúsculas o minúsculas y se normaliza a minúsculas en el servicio.
 */
public record RegisterRequest(
        @NotNull(message = "El nombre de usuario es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9_.]{3,30}$",
                message = "El nombre de usuario debe tener entre 3 y 30 caracteres: letras, números, '_' o '.'")
        String username,

        @NotNull(message = "La contraseña es obligatoria")
        @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres")
        @MaxUtf8Bytes(value = 72, message = "La contraseña es demasiado larga")
        String password,

        @NotNull(message = "El rango etario es obligatorio")
        AgeRange ageRange) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***, ageRange=" + ageRange + "]";
    }
}
