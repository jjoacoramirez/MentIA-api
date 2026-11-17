package com.mentia.security;

/**
 * {@code ADMIN} habilita herramientas de debug (por ejemplo, saltear la evaluación inicial).
 * Se asigna a mano en la base; ver README.
 */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
