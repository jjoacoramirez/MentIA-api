package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;

/**
 * Chequeos estáticos que no necesitan base: los enums de Java coinciden con el contrato y el seed cubre todos los tipos.
 */
class CatalogContractTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V3__catalog_exercises.sql");
    private static final Path VARIANTS_MIGRATION = Path.of("src/main/resources/db/migration/V7__exercise_variants.sql");

    @Test
    void exerciseTypeCoincideConElContrato() throws IOException {
        assertThat(contractEnum("ExerciseType")).containsExactlyElementsOf(names(ExerciseType.values()));
    }

    @Test
    void modalityCoincideConElContrato() throws IOException {
        assertThat(contractEnum("Modality")).containsExactlyElementsOf(names(Modality.values()));
    }

    @Test
    void elSeedDefineParametrosYDuracionParaCadaTipo() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (ExerciseType type : ExerciseType.values()) {
            String when = "WHEN '" + type.name() + "'";
            // Un WHEN para params, otro para estimated_seconds.
            assertThat(sql.split(java.util.regex.Pattern.quote(when), -1).length - 1)
                    .as("ramas CASE para %s", type)
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void cadaMecanicaActivaTieneAlMenosTresVariantesEnElSeed() throws IOException {
        String sql = Files.readString(VARIANTS_MIGRATION);
        for (ExerciseType type : ExerciseType.values()) {
            if (type == ExerciseType.AUDITORY_SEQUENCE) {
                continue; // Inactivo en el seed de BACK-04.
            }
            long variants = sql.lines().filter(line -> line.strip().startsWith("('" + type.name() + "',")).count();
            assertThat(variants).as("variantes de %s", type).isGreaterThanOrEqualTo(3);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> contractEnum(String schema) throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("openapi.yaml"))) {
            Map<String, Object> root = new Yaml().load(reader);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            return (List<String>) ((Map<String, Object>) schemas.get(schema)).get("enum");
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
