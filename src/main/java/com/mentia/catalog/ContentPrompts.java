package com.mentia.catalog;

import java.util.List;

import com.mentia.ai.AiRequest;
import com.mentia.catalog.domain.ExerciseType;

/**
 * Prompts de la generación de contenido. Llevan solo el tipo de ejercicio y los temas que ya existen: nunca ids,
 * usuarios ni datos de nadie.
 */
final class ContentPrompts {

    private static final String SYSTEM = """
            Sos un asistente que arma material para un juego de memoria y atención en español rioplatense. \
            Respondé SOLO con un objeto JSON válido, sin texto antes ni después y sin bloques de código.""";

    private ContentPrompts() {
    }

    static AiRequest forType(ExerciseType type, List<String> existingThemes, int maxTokens) {
        String avoid = existingThemes.isEmpty() ? "" : "\nTemas que ya existen y NO podés repetir: "
                + String.join(", ", existingThemes) + ".";
        String task = switch (type) {
            case VISUAL_RECOGNITION -> """
                    Inventá un tema nuevo de imágenes para un juego de memoria visual.
                    Formato: {"theme": "<tema de 1 a 3 palabras en minúsculas>", "items": [<exactamente 24 emojis distintos>]}
                    Los emojis tienen que ser de objetos, animales, comidas o lugares, fáciles de distinguir entre sí. \
                    Sin rostros, sin personas ni manos, sin banderas y sin emojis compuestos.""";
            case REVERSE_SEQUENCE -> """
                    Inventá un conjunto nuevo de símbolos para un juego de repetir secuencias al revés.
                    Formato: {"theme": "<tema de 1 a 3 palabras en minúsculas>", "symbols": [<entre 8 y 12 símbolos distintos>]}
                    Los símbolos son letras MAYÚSCULAS consonantes (sin vocales) o emojis de objetos, animales o comidas, \
                    pero todos del mismo tipo y fáciles de distinguir entre sí. Sin rostros, personas, manos ni banderas.""";
            case N_BACK -> """
                    Inventá un conjunto nuevo de símbolos para un juego de memoria de trabajo tipo N-back.
                    Formato: {"theme": "<tema de 1 a 3 palabras en minúsculas>", "symbols": [<entre 6 y 10 símbolos distintos>]}
                    Los símbolos son letras MAYÚSCULAS consonantes (sin vocales), figuras geométricas (como ● ■ ▲ ◆ ★) \
                    o emojis de objetos, animales o comidas, pero todos del mismo tipo y fáciles de distinguir entre sí. \
                    Sin rostros, personas, manos ni banderas.""";
            default -> throw new IllegalArgumentException("Tipo de ejercicio no soportado: " + type);
        };
        return new AiRequest(SYSTEM, task + avoid, maxTokens);
    }
}
