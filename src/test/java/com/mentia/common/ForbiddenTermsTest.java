package com.mentia.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Regla de negocio 2 (CLAUDE.md): nada de lenguaje clínico en código, mensajes, seeds ni contrato.
 */
class ForbiddenTermsTest {

    // Sin tildes: el texto se normaliza antes de comparar.
    private static final List<String> FORBIDDEN = List.of(
            "diagnostico", "capacidad de memoria", "mejora cognitiva", "deficit");

    @Test
    void elCodigoLasMigracionesYElContratoNoUsanTerminosClinicos() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            for (Path file : Stream.concat(files.filter(Files::isRegularFile), Stream.of(Path.of("openapi.yaml"))).toList()) {
                String text = normalize(Files.readString(file));
                FORBIDDEN.stream().filter(text::contains).forEach(term -> hits.add(file + ": " + term));
            }
        }
        assertThat(hits).isEmpty();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
