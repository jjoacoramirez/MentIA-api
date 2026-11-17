package com.mentia.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class StreaksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Test
    void cuentaDiasSeguidosHastaHoy() {
        assertThat(Streaks.current(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4)), TODAY))
                .isEqualTo(3);
    }

    @Test
    void siHoyTodaviaNoEntrenoLaRachaSigueDesdeAyer() {
        assertThat(Streaks.current(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void siAyerNoEntrenoLaRachaSeCorto() {
        assertThat(Streaks.current(List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY)).isZero();
        assertThat(Streaks.current(List.of(), TODAY)).isZero();
    }
}
