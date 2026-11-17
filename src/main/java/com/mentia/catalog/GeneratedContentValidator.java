package com.mentia.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.catalog.domain.ExerciseType;

/**
 * Valida el contenido que devuelve la IA antes de guardarlo. Es lógica pura y estricta: la IA propone y esta clase
 * decide. Lo que no cumple se descarta entero, nunca se "arregla".
 *
 * <p>El filtro de emojis es de mejor esfuerzo: deja afuera rostros, personas, banderas, tonos de piel y secuencias
 * con unión (ZWJ). Por eso lo generado queda marcado como {@code AI} y se puede revisar o desactivar.
 */
final class GeneratedContentValidator {

    /** Ejercicios cuyo contenido se puede generar: una lista de elementos por variante. */
    static final Set<ExerciseType> SUPPORTED = Set.of(ExerciseType.VISUAL_RECOGNITION, ExerciseType.REVERSE_SEQUENCE,
            ExerciseType.N_BACK);

    private static final Pattern THEME = Pattern.compile("^[\\p{L}]+( [\\p{L}]+){0,3}$");
    private static final Pattern CONSONANT = Pattern.compile("^[BCDFGHJKLMNPRSTVWXYZ]$");
    private static final Pattern SHAPE = Pattern.compile("^[\\u25A0-\\u25FF\\u2605\\u2606]$");
    private static final Pattern EMOJI = Pattern.compile("^\\p{IsExtended_Pictographic}\\uFE0F?$");
    /** Con 60% o más de elementos en común con otra variante, es la misma variante con otro nombre. */
    private static final double MAX_OVERLAP = 0.6;
    private static final int MAX_THEME_LENGTH = 40;

    private GeneratedContentValidator() {
    }

    /** Contenido listo para guardar: el tema y el {@code content} de la variante. */
    record Candidate(String theme, Map<String, Object> content) {
    }

    /** Variante ya existente del ejercicio, para no repetir tema ni elementos. */
    record Existing(String theme, Set<String> elements) {
    }

    static final class InvalidContentException extends RuntimeException {
        InvalidContentException(String message) {
            super(message);
        }
    }

    /** Nombre de la lista según el tipo: {@code items} para reconocimiento y {@code symbols} para el resto. */
    static String listField(ExerciseType type) {
        return type == ExerciseType.VISUAL_RECOGNITION ? "items" : "symbols";
    }

    static Candidate validate(ExerciseType type, JsonNode json, List<Existing> existing) {
        if (!SUPPORTED.contains(type)) {
            throw new InvalidContentException("Tipo de ejercicio no soportado: " + type);
        }
        if (json == null || !json.isObject()) {
            throw new InvalidContentException("La respuesta no es un objeto JSON");
        }
        String theme = theme(json.get("theme"));
        List<String> elements = elements(json.get(listField(type)));
        switch (type) {
            case VISUAL_RECOGNITION -> {
                requireSize(elements, 24, 24);
                requireAll(elements, GeneratedContentValidator::isSafeEmoji, "Solo se aceptan emojis de objetos");
            }
            case REVERSE_SEQUENCE -> {
                requireSize(elements, 8, 12);
                requireSameKind(elements, true);
            }
            case N_BACK -> {
                requireSize(elements, 6, 10);
                requireSameKind(elements, false);
            }
            default -> throw new InvalidContentException("Tipo de ejercicio no soportado: " + type);
        }
        String normalizedTheme = normalize(theme);
        for (Existing other : existing) {
            if (normalize(other.theme()).equals(normalizedTheme)) {
                throw new InvalidContentException("Ya existe una variante con ese tema");
            }
            if (overlap(elements, other.elements()) >= MAX_OVERLAP) {
                throw new InvalidContentException("Se parece demasiado a una variante existente");
            }
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("theme", theme);
        content.put(listField(type), elements);
        return new Candidate(theme, content);
    }

    private static String theme(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new InvalidContentException("Falta el tema");
        }
        String theme = node.asText().trim().toLowerCase(Locale.ROOT);
        if (theme.length() > MAX_THEME_LENGTH || !THEME.matcher(theme).matches()) {
            throw new InvalidContentException("El tema no es válido");
        }
        return theme;
    }

    private static List<String> elements(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new InvalidContentException("Falta la lista de elementos");
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new InvalidContentException("Hay un elemento que no es texto");
            }
            list.add(item.asText().trim());
        }
        if (new LinkedHashSet<>(list).size() != list.size()) {
            throw new InvalidContentException("Hay elementos repetidos");
        }
        return list;
    }

    private static void requireSize(List<String> elements, int min, int max) {
        if (elements.size() < min || elements.size() > max) {
            throw new InvalidContentException("Cantidad de elementos inválida: " + elements.size());
        }
    }

    private static void requireAll(List<String> elements, java.util.function.Predicate<String> rule, String message) {
        if (!elements.stream().allMatch(rule)) {
            throw new InvalidContentException(message);
        }
    }

    /** Todos del mismo tipo: consonantes, o emojis (y, en N-back, también figuras geométricas). */
    private static void requireSameKind(List<String> elements, boolean emojiOnly) {
        boolean letters = elements.stream().allMatch(e -> CONSONANT.matcher(e).matches());
        boolean pictures = elements.stream().allMatch(e -> isSafeEmoji(e) || (!emojiOnly && SHAPE.matcher(e).matches()));
        if (!letters && !pictures) {
            throw new InvalidContentException("Los elementos no son todos del mismo tipo permitido");
        }
    }

    static boolean isSafeEmoji(String value) {
        if (!EMOJI.matcher(value).matches()) {
            return false;
        }
        int codePoint = value.codePointAt(0);
        boolean face = codePoint >= 0x1F600 && codePoint <= 0x1F64F;
        boolean gestureOrPerson = (codePoint >= 0x1F910 && codePoint <= 0x1F93A) || (codePoint >= 0x1F464 && codePoint <= 0x1F46F)
                || (codePoint >= 0x1F9D0 && codePoint <= 0x1F9DF) || (codePoint >= 0x1F970 && codePoint <= 0x1F97A)
                || (codePoint >= 0x1F446 && codePoint <= 0x1F450);
        boolean flag = codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
        boolean skinTone = codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
        return !(face || gestureOrPerson || flag || skinTone);
    }

    private static double overlap(List<String> elements, Set<String> other) {
        if (other.isEmpty() || elements.isEmpty()) {
            return 0;
        }
        long shared = elements.stream().filter(other::contains).count();
        return (double) shared / Math.min(elements.size(), other.size());
    }

    /** Minúsculas y sin tildes, para comparar temas. */
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
