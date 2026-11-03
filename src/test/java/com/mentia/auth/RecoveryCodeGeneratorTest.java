package com.mentia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RecoveryCodeGeneratorTest {

    private final RecoveryCodeGenerator generator = new RecoveryCodeGenerator();

    @Test
    void elAlfabetoTiene32SimbolosSinAmbiguos() {
        assertThat(RecoveryCodeGenerator.ALPHABET).hasSize(32).doesNotContain("0", "O", "1", "I");
        assertThat(RecoveryCodeGenerator.ALPHABET.chars().distinct().count()).isEqualTo(32);
    }

    @Test
    void generaVeinteCaracteresEnGruposDeCinco() {
        String alphabet = "[" + RecoveryCodeGenerator.ALPHABET + "]";
        String code = generator.generate();
        assertThat(code).matches(alphabet + "{5}-" + alphabet + "{5}-" + alphabet + "{5}-" + alphabet + "{5}");
    }

    @Test
    void noRepiteCodigos() {
        Set<String> codes = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> codes.add(generator.generate()));
        assertThat(codes).hasSize(10_000);
    }

    @Test
    void conLaMismaSemillaGeneraLoMismo() {
        String first = new RecoveryCodeGenerator(new SplittableRandom(42)).generate();
        String second = new RecoveryCodeGenerator(new SplittableRandom(42)).generate();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void normalizaComoSeaQueSeHayaCopiado() {
        assertThat(RecoveryCodeGenerator.normalize("7kq2m x9ptd-4hbwe\trc6zn")).isEqualTo("7KQ2MX9PTD4HBWERC6ZN");
        assertThat(RecoveryCodeGenerator.normalize("7KQ2M-X9PTD-4HBWE-RC6ZN")).isEqualTo("7KQ2MX9PTD4HBWERC6ZN");
    }
}
