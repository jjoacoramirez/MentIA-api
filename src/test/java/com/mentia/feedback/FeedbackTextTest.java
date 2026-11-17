package com.mentia.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mentia.ai.AiRequest;

class FeedbackTextTest {

    private static final List<String> FORBIDDEN = List.of("diagnostic", "capacidad", "cognitiv", "defici", "medic");

    private static Map<String, Object> result(String type, double accuracy, Double delta, int before, int after) {
        Map<String, Object> category = new java.util.LinkedHashMap<>();
        category.put("category", "ATENCION");
        category.put("accuracy", accuracy);
        category.put("difficultyLevelBefore", before);
        category.put("difficultyLevelAfter", after);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", UUID.randomUUID().toString());
        result.put("type", type);
        result.put("itemsTotal", 6);
        result.put("itemsCompleted", 6);
        result.put("accuracy", accuracy);
        result.put("categories", List.of(category));
        if (delta != null) {
            result.put("comparison", Map.of("previousAccuracy", accuracy - delta, "accuracyDelta", delta));
        }
        return result;
    }

    private static FeedbackFacts facts(Map<String, Object> result) {
        return FeedbackFacts.from(result, Map.of("ATENCION", "Atención"));
    }

    @Test
    void factsKeepOnlyNumbersAndAreaNames() {
        FeedbackFacts facts = facts(result("TRAINING", 0.82, 0.07, 3, 4));

        assertThat(facts.accuracyPct()).isEqualTo(82);
        assertThat(facts.deltaPoints()).isEqualTo(7);
        assertThat(facts.areas()).singleElement().satisfies(area -> {
            assertThat(area.name()).isEqualTo("Atención");
            assertThat(area.rose()).isTrue();
        });
    }

    @Test
    void promptCarriesNoIdsAndAsksForANonClinicalToneWithoutComparingPeople() {
        Map<String, Object> result = result("TRAINING", 0.82, 0.07, 3, 4);
        AiRequest request = FeedbackText.request(facts(result), 200);

        assertThat(request.userPrompt()).contains("6 de 6", "82%", "+7", "Atención", "subió del nivel 3 al 4")
                .doesNotContain(String.valueOf(result.get("sessionId")));
        assertThat(request.systemPrompt()).contains("no hagas afirmaciones médicas", "no la compares con otras personas",
                "voseo");
    }

    @Test
    void initialAssessmentDoesNotInventALevelChange() {
        FeedbackFacts facts = facts(result("INITIAL_ASSESSMENT", 0.7, null, 3, 3));

        assertThat(FeedbackText.request(facts, 200).userPrompt()).contains("evaluación inicial", "nivel inicial 3")
                .doesNotContain("subió", "bajó");
        assertThat(FeedbackText.basic(facts)).contains("evaluación inicial", "punto de partida");
    }

    @Test
    void basicTextAdaptsToTheResultAndNeverBlamesTheUser() {
        assertThat(FeedbackText.basic(facts(result("TRAINING", 0.9, 0.08, 3, 4))))
                .contains("¡Muy buena sesión!", "90%", "Subiste de nivel en Atención", "8 puntos");
        assertThat(FeedbackText.basic(facts(result("TRAINING", 0.7, 0.01, 3, 3))))
                .contains("Cada intento suma práctica").doesNotContain("Subiste", "puntos respecto");
        assertThat(FeedbackText.basic(facts(result("TRAINING", 0.4, null, 3, 2))))
                .contains("Equivocarte es parte de practicar");
    }

    @Test
    void basicTextNeverUsesForbiddenTerms() {
        for (double accuracy : List.of(0.2, 0.7, 0.95)) {
            assertThat(FeedbackText.sanitize(FeedbackText.basic(facts(result("TRAINING", accuracy, 0.1, 3, 4))), 450,
                    FORBIDDEN)).isPresent();
        }
    }

    @Test
    void sanitizeCleansFormattingAndKeepsGoodText() {
        assertThat(FeedbackText.sanitize("  **¡Muy bien!**  Completaste todo\ncon buena precisión.  ", 450, FORBIDDEN))
                .contains("¡Muy bien! Completaste todo con buena precisión.");
    }

    @Test
    void sanitizeDiscardsClinicalTermsEvenWithAccentsOrCapitals() {
        for (String text : List.of("Tu CAPACIDAD de memoria mejoró muchísimo hoy.", "Esto es un Diagnóstico de tu atención.",
                "Hay un déficit en tu atención, ojo.", "Mejoró tu rendimiento cognitivo esta semana.")) {
            assertThat(FeedbackText.sanitize(text, 450, FORBIDDEN)).as(text).isEmpty();
        }
    }

    @Test
    void sanitizeDiscardsTooShortLinksAndBlank() {
        assertThat(FeedbackText.sanitize("Bien.", 450, FORBIDDEN)).isEmpty();
        assertThat(FeedbackText.sanitize("Mirá más en https://ejemplo.com todo el detalle.", 450, FORBIDDEN)).isEmpty();
        assertThat(FeedbackText.sanitize("   ", 450, FORBIDDEN)).isEmpty();
        assertThat(FeedbackText.sanitize(null, 450, FORBIDDEN)).isEmpty();
    }

    @Test
    void sanitizeCutsALongTextAtTheLastCompleteSentenceOrDiscardsIt() {
        String longText = "Muy buena sesión, completaste todo. Seguí así en la próxima. " + "y".repeat(300);

        assertThat(FeedbackText.sanitize(longText, 100, FORBIDDEN))
                .contains("Muy buena sesión, completaste todo. Seguí así en la próxima.");
        // Sin ninguna oración completa dentro del tope no hay dónde cortar.
        assertThat(FeedbackText.sanitize("z".repeat(500), 100, FORBIDDEN)).isEmpty();
    }
}
