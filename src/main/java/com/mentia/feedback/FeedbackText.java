package com.mentia.feedback;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mentia.ai.AiRequest;

/**
 * Los textos del comentario: el prompt para la IA, el texto básico de respaldo y la limpieza de lo que devuelve la IA.
 * Lógica pura. Todo habla del desempeño dentro de MentIA: nada clínico, sin compararse con otras personas.
 */
final class FeedbackText {

    private static final String SYSTEM = """
            Sos el acompañante de MentIA, una app donde la gente practica ejercicios de memoria y atención. \
            Escribí en español rioplatense (voseo), en 2 o 3 oraciones y con un máximo de 55 palabras, con un tono \
            cálido y concreto. Mencioná solo los datos que te paso y no inventes nada. \
            Reglas: hablá del desempeño de la persona dentro de MentIA; no hagas afirmaciones médicas ni de salud \
            ni sobre sus capacidades; no la compares con otras personas; si hubo errores, normalizalos como parte de \
            practicar; no prometas resultados. Sin listas, sin títulos, sin emojis y sin comillas.""";

    private static final int MIN_CHARS = 20;

    private FeedbackText() {
    }

    /** Pedido a la IA: solo los datos numéricos de la sesión. */
    static AiRequest request(FeedbackFacts facts, int maxTokens) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(facts.assessment() ? "Terminó su evaluación inicial. " : "Terminó una sesión de entrenamiento. ")
                .append("Completó ").append(facts.itemsCompleted()).append(" de ").append(facts.itemsTotal())
                .append(" actividades con ").append(facts.accuracyPct()).append("% de precisión general.");
        if (facts.deltaPoints() != null) {
            prompt.append(" Respecto de su sesión anterior, la precisión cambió ").append(signed(facts.deltaPoints()))
                    .append(" puntos.");
        }
        if (!facts.areas().isEmpty()) {
            prompt.append("\nPor área: ");
            prompt.append(facts.areas().stream().map(FeedbackText::describe).collect(Collectors.joining("; "))).append('.');
        }
        prompt.append("\nEscribí el comentario.");
        return new AiRequest(SYSTEM, prompt.toString(), maxTokens);
    }

    private static String describe(FeedbackFacts.Area area) {
        String level = area.levelBefore() == null ? "nivel inicial " + area.levelAfter()
                : area.levelAfter() > area.levelBefore() ? "subió del nivel " + area.levelBefore() + " al " + area.levelAfter()
                : area.levelAfter() < area.levelBefore() ? "bajó del nivel " + area.levelBefore() + " al " + area.levelAfter()
                : "se mantuvo en el nivel " + area.levelAfter();
        return area.name() + ": " + area.accuracyPct() + "% de precisión, " + level;
    }

    private static String signed(int points) {
        return points > 0 ? "+" + points : String.valueOf(points);
    }

    /** Texto de respaldo, armado con los mismos datos y sin IA. */
    static String basic(FeedbackFacts facts) {
        StringBuilder text = new StringBuilder();
        String done = facts.itemsCompleted() + (facts.itemsCompleted() == 1 ? " actividad" : " actividades");
        if (facts.assessment()) {
            text.append("Terminaste tu evaluación inicial: ").append(done).append(" con ").append(facts.accuracyPct())
                    .append("% de precisión. Con esto armamos tu punto de partida en MentIA.");
            return text.toString();
        }
        if (facts.accuracyPct() >= 85) {
            text.append("¡Muy buena sesión! Completaste ").append(done).append(" con ").append(facts.accuracyPct())
                    .append("% de precisión.");
        } else if (facts.accuracyPct() >= 60) {
            text.append("Sesión completa: ").append(done).append(" con ").append(facts.accuracyPct())
                    .append("% de precisión. Cada intento suma práctica.");
        } else {
            text.append("Completaste ").append(done).append(". Equivocarte es parte de practicar: lo importante es que volviste a intentarlo.");
        }
        facts.areas().stream().filter(FeedbackFacts.Area::rose).findFirst()
                .ifPresent(area -> text.append(" Subiste de nivel en ").append(area.name()).append('.'));
        if (facts.deltaPoints() != null && facts.deltaPoints() >= 5) {
            text.append(" Tu precisión subió ").append(facts.deltaPoints()).append(" puntos respecto de tu sesión anterior.");
        }
        return text.toString();
    }

    /**
     * Limpia lo que devolvió la IA y lo descarta si no cumple: muy corto, con un término prohibido, con un enlace, o
     * demasiado largo para cortarlo en una oración completa.
     */
    static Optional<String> sanitize(String raw, int maxChars, List<String> forbiddenTerms) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.replaceAll("[*_#`>\\[\\]\"“”]", "").replaceAll("\\s+", " ").trim();
        if (text.length() > maxChars) {
            int cut = Math.max(text.lastIndexOf('.', maxChars - 1), Math.max(text.lastIndexOf('!', maxChars - 1),
                    text.lastIndexOf('?', maxChars - 1)));
            text = cut >= MIN_CHARS ? text.substring(0, cut + 1) : "";
        }
        if (text.length() < MIN_CHARS || text.toLowerCase(Locale.ROOT).contains("http")) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        boolean forbidden = forbiddenTerms.stream().map(FeedbackText::normalize).anyMatch(normalized::contains);
        return forbidden ? Optional.empty() : Optional.of(text);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
