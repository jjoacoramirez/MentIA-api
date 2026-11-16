package com.mentia.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mentia.engine.DifficultyEngine.Activity;
import com.mentia.engine.DifficultyEngine.Adjustment;
import com.mentia.engine.DifficultyEngine.Decision;

class DifficultyEngineTest {

    private static final DifficultyEngine ENGINE = new DifficultyEngine(
            new AdaptationProperties(0.85, 0.60, 1.25, new AdaptationProperties.LevelProgress(50, 25, 10, 25)),
            new EngineProperties(3, 1, 10, 0.3));

    private static Activity activity(double accuracy) {
        return new Activity(accuracy, null);
    }

    @ParameterizedTest(name = "precisión {0}, tiempo {1} → {2} puntos")
    @CsvSource(nullValues = "-", value = {
            // Precisión alta: suma todo si está a tiempo (o el ejercicio no mide tiempo), menos si es lenta.
            "1.00, -,    25",
            "0.85, 1.00, 25",
            "0.85, 1.25, 25",
            "0.85, 1.26, 10",
            // Precisión media: no cambia.
            "0.84, -,    0",
            "0.60, 3.00, 0",
            // Precisión baja: resta, sin importar el tiempo.
            "0.59, -,    -25",
            "0.00, 0.50, -25"})
    void puntosPorActividad(double accuracy, Double timeRatio, int points) {
        assertThat(ENGINE.points(new Activity(accuracy, timeRatio))).isEqualTo(points);
    }

    @Test
    void sinLlegarAUnExtremoSeQuedaEnElNivelConElProgresoActualizado() {
        assertThat(ENGINE.next(4, 50, List.of(activity(0.0)))).isEqualTo(new Decision(4, 25, -25, Adjustment.KEEP));
        assertThat(ENGINE.next(4, 50, List.of(activity(1.0), activity(0.7)))).isEqualTo(
                new Decision(4, 75, 25, Adjustment.KEEP));
        assertThat(ENGINE.next(4, 50, List.of())).isEqualTo(new Decision(4, 50, 0, Adjustment.KEEP));
    }

    @Test
    void unSoloJuegoMaloDesdeElPisoBajaDeNivel() {
        assertThat(ENGINE.next(4, 25, List.of(activity(0.0)))).isEqualTo(new Decision(3, 50, -25, Adjustment.DOWN));
    }

    @Test
    void unSoloJuegoBuenoDesdeElTechoSubeDeNivel() {
        assertThat(ENGINE.next(4, 75, List.of(activity(1.0)))).isEqualTo(new Decision(5, 50, 25, Adjustment.UP));
    }

    @Test
    void desdeElMedioHacenFaltaDosJuegos() {
        assertThat(ENGINE.next(4, 50, List.of(activity(1.0), activity(0.9))).adjustment()).isEqualTo(Adjustment.UP);
        assertThat(ENGINE.next(4, 50, List.of(activity(0.2), activity(0.5))).adjustment()).isEqualTo(Adjustment.DOWN);
    }

    @Test
    void unaSesionMueveComoMuchoUnNivel() {
        List<Activity> great = List.of(activity(1.0), activity(1.0), activity(1.0), activity(1.0), activity(1.0));
        assertThat(ENGINE.next(4, 90, great)).isEqualTo(new Decision(5, 50, 125, Adjustment.UP));
    }

    @Test
    void enLosExtremosDelRangoElProgresoQuedaEnElTechoOEnElPiso() {
        assertThat(ENGINE.next(10, 90, List.of(activity(1.0)))).isEqualTo(new Decision(10, 100, 25, Adjustment.KEEP));
        assertThat(ENGINE.next(1, 10, List.of(activity(0.0)))).isEqualTo(new Decision(1, 0, -25, Adjustment.KEEP));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1",
            "12, 10"})
    void unNivelFueraDeRangoSeAcotaAntesDeDecidir(int current, int expected) {
        assertThat(ENGINE.next(current, 50, List.of(activity(0.7))).level()).isEqualTo(expected);
    }
}
