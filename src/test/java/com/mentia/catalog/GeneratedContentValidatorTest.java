package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.catalog.GeneratedContentValidator.Candidate;
import com.mentia.catalog.GeneratedContentValidator.Existing;
import com.mentia.catalog.GeneratedContentValidator.InvalidContentException;
import com.mentia.catalog.domain.ExerciseType;

class GeneratedContentValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TWENTY_FOUR = List.of("🌵", "🌲", "🌴", "🌻", "🌷", "🍀", "🍁", "🍄", "🌾", "🌿", "🪴", "🌹",
            "🪨", "🏔️", "🌋", "🏝️", "🏜️", "⛰️", "🌊", "🔥", "💧", "❄️", "⭐", "🌙");

    private static JsonNode json(String theme, List<String> items, String field) throws Exception {
        String list = items.stream().map(i -> "\"" + i + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return MAPPER.readTree("{\"theme\": \"" + theme + "\", \"" + field + "\": [" + list + "]}");
    }

    private static void rejects(ExerciseType type, JsonNode node, List<Existing> existing, String reason) {
        assertThatThrownBy(() -> GeneratedContentValidator.validate(type, node, existing))
                .isInstanceOf(InvalidContentException.class).hasMessageContaining(reason);
    }

    @Test
    void acceptsAValidVisualRecognitionVariant() throws Exception {
        Candidate candidate = GeneratedContentValidator.validate(ExerciseType.VISUAL_RECOGNITION,
                json("naturaleza", TWENTY_FOUR, "items"), List.of());

        assertThat(candidate.theme()).isEqualTo("naturaleza");
        assertThat(candidate.content()).containsEntry("theme", "naturaleza").containsEntry("items", TWENTY_FOUR);
    }

    @Test
    void visualRecognitionNeedsExactlyTwentyFourDistinctEmojis() throws Exception {
        rejects(ExerciseType.VISUAL_RECOGNITION, json("naturaleza", TWENTY_FOUR.subList(0, 23), "items"), List.of(),
                "Cantidad");
        List<String> repeated = new java.util.ArrayList<>(TWENTY_FOUR);
        repeated.set(23, repeated.get(0));
        rejects(ExerciseType.VISUAL_RECOGNITION, json("naturaleza", repeated, "items"), List.of(), "repetidos");
        List<String> withLetter = new java.util.ArrayList<>(TWENTY_FOUR);
        withLetter.set(5, "A");
        rejects(ExerciseType.VISUAL_RECOGNITION, json("naturaleza", withLetter, "items"), List.of(), "emojis");
    }

    @ParameterizedTest
    @ValueSource(strings = { "😀", "😡", "👨", "🧑", "👋", "🇦🇷", "👍🏽", "👨‍👩‍👧" })
    void rejectsFacesPeopleHandsFlagsSkinTonesAndComposedEmojis(String unsafe) throws Exception {
        List<String> items = new java.util.ArrayList<>(TWENTY_FOUR);
        items.set(0, unsafe);
        rejects(ExerciseType.VISUAL_RECOGNITION, json("naturaleza", items, "items"), List.of(), "emojis");
    }

    @Test
    void keepsAnimalsFoodAndObjectsAsSafe() {
        for (String emoji : List.of("🐶", "🦊", "🍎", "🏀", "🪁", "🫐", "🔑")) {
            assertThat(GeneratedContentValidator.isSafeEmoji(emoji)).as(emoji).isTrue();
        }
    }

    @Test
    void reverseSequenceAcceptsConsonantsOrEmojisButNotMixedOrVowels() throws Exception {
        List<String> consonants = List.of("B", "C", "D", "F", "G", "H", "J", "K");
        assertThat(GeneratedContentValidator.validate(ExerciseType.REVERSE_SEQUENCE,
                json("consonantes", consonants, "symbols"), List.of()).content()).containsEntry("symbols", consonants);

        rejects(ExerciseType.REVERSE_SEQUENCE, json("con vocales", List.of("B", "C", "D", "F", "G", "H", "J", "A"), "symbols"),
                List.of(), "mismo tipo");
        rejects(ExerciseType.REVERSE_SEQUENCE, json("mezcla", List.of("B", "C", "D", "F", "G", "H", "J", "🐶"), "symbols"),
                List.of(), "mismo tipo");
        rejects(ExerciseType.REVERSE_SEQUENCE, json("corto", consonants.subList(0, 7), "symbols"), List.of(), "Cantidad");
    }

    @Test
    void nBackAcceptsGeometricShapesButSequencesDoNot() throws Exception {
        List<String> shapes = List.of("●", "■", "▲", "◆", "★", "◼");
        assertThat(GeneratedContentValidator.validate(ExerciseType.N_BACK, json("figuras", shapes, "symbols"), List.of())
                .theme()).isEqualTo("figuras");
        rejects(ExerciseType.REVERSE_SEQUENCE, json("figuras", List.of("●", "■", "▲", "◆", "★", "◼", "◻", "◽"), "symbols"),
                List.of(), "mismo tipo");
    }

    @Test
    void rejectsBadThemes() throws Exception {
        for (String theme : List.of("", "tema 2", "a b c d e", "x".repeat(41), "tema!")) {
            rejects(ExerciseType.VISUAL_RECOGNITION, json(theme, TWENTY_FOUR, "items"), List.of(), "tema");
        }
    }

    @Test
    void doesNotRepeatAnExistingThemeEvenWithDifferentCaseOrAccents() throws Exception {
        rejects(ExerciseType.VISUAL_RECOGNITION, json("Naturaleza", TWENTY_FOUR, "items"),
                List.of(new Existing("naturaleza", Set.of())), "tema");
        rejects(ExerciseType.VISUAL_RECOGNITION, json("selvática", TWENTY_FOUR, "items"),
                List.of(new Existing("Selvatica", Set.of())), "tema");
    }

    @Test
    void doesNotRepeatAnExistingVariantUnderAnotherName() throws Exception {
        Existing same = new Existing("otra cosa", Set.copyOf(TWENTY_FOUR.subList(0, 20)));
        rejects(ExerciseType.VISUAL_RECOGNITION, json("naturaleza", TWENTY_FOUR, "items"), List.of(same), "parece");
        // Solo 5 de 24 en común: es otra variante.
        java.util.Set<String> mostlyOther = new java.util.HashSet<>(TWENTY_FOUR.subList(0, 5));
        for (int i = 0; i < 19; i++) mostlyOther.add("otro" + i);
        Existing different = new Existing("otra cosa", mostlyOther);
        assertThat(GeneratedContentValidator.validate(ExerciseType.VISUAL_RECOGNITION,
                json("naturaleza", TWENTY_FOUR, "items"), List.of(different)).theme()).isEqualTo("naturaleza");
    }

    @Test
    void rejectsMalformedAndUnsupportedInput() throws Exception {
        rejects(ExerciseType.VISUAL_RECOGNITION, MAPPER.readTree("[1,2]"), List.of(), "objeto");
        rejects(ExerciseType.VISUAL_RECOGNITION, MAPPER.readTree("{\"items\": []}"), List.of(), "tema");
        rejects(ExerciseType.VISUAL_RECOGNITION, MAPPER.readTree("{\"theme\": \"x\"}"), List.of(), "lista");
        rejects(ExerciseType.VISUAL_RECOGNITION, MAPPER.readTree("{\"theme\": \"naturaleza\"}"), List.of(), "lista");
        rejects(ExerciseType.VISUAL_RECOGNITION, MAPPER.readTree("{\"theme\": \"naturaleza\", \"items\": [1, 2]}"), List.of(),
                "texto");
        rejects(ExerciseType.STROOP, json("colores", List.of("a"), "symbols"), List.of(), "no soportado");
    }
}
