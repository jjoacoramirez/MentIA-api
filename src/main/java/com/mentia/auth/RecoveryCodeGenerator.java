package com.mentia.auth;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

/**
 * Códigos de recuperación de 20 caracteres de un alfabeto de 32 símbolos (100 bits), en grupos de 5:
 * {@code 7KQ2M-X9PTD-4HBWE-RC6ZN}. El alfabeto excluye los símbolos que se confunden al copiarlos (0/O, 1/I).
 */
@Component
public class RecoveryCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int LENGTH = 20;
    private static final int GROUP = 5;

    private final RandomGenerator random;

    public RecoveryCodeGenerator() {
        this(new SecureRandom());
    }

    RecoveryCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** Código formateado para mostrar al usuario. */
    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH + LENGTH / GROUP);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % GROUP == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Forma canónica que se hashea y se compara: sin guiones ni espacios y en mayúsculas,
     * para aceptar el código como sea que el usuario lo haya copiado.
     */
    public static String normalize(String code) {
        return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
