package com.mentia.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiRequest;
import com.mentia.ai.AiResponse;
import com.mentia.ai.AiUnavailableException;
import com.mentia.ai.GuardedAiService;
import com.mentia.catalog.GeneratedContentValidator.Candidate;
import com.mentia.catalog.GeneratedContentValidator.Existing;
import com.mentia.catalog.GeneratedContentValidator.InvalidContentException;
import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.ExerciseVariant;
import com.mentia.catalog.domain.VariantSource;
import com.mentia.catalog.dto.GenerateContentResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.common.error.FieldValidationException;

/**
 * Generación de contenido por lotes con IA (IA-01, uso a). La dispara un admin, nunca una sesión de entrenamiento.
 * Cada llamada pide UNA variante nueva; la respuesta se valida de forma estricta y, si no cumple, se descarta.
 *
 * <p>La IA no decide nada del motor: solo propone listas de estímulos que entran como filas de
 * {@code exercise_variants} (regla 4: extensible por datos). El costo es global y lo limita el guard de IA.
 */
@Service
public class ContentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationService.class);

    private final GuardedAiService ai;
    private final ExerciseRepository exercises;
    private final ExerciseVariantRepository variants;
    private final ObjectMapper mapper;
    private final ContentGenerationProperties properties;

    public ContentGenerationService(GuardedAiService ai, ExerciseRepository exercises,
            ExerciseVariantRepository variants, ObjectMapper mapper, ContentGenerationProperties properties) {
        this.ai = ai;
        this.exercises = exercises;
        this.variants = variants;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Sin transacción: mientras se espera a la IA no se mantiene abierta una conexión a la base; cada variante se
     * guarda en su propia transacción.
     *
     * @param count variantes nuevas a pedir por cada ejercicio activo de ese tipo
     */
    public GenerateContentResponse generate(ExerciseType type, int count) {
        if (!GeneratedContentValidator.SUPPORTED.contains(type)) {
            throw new FieldValidationException("exerciseType", "Ese tipo de ejercicio no admite contenido generado");
        }
        if (count < 1 || count > properties.maxVariantsPerRequest()) {
            throw new FieldValidationException("count",
                    "Podés pedir entre 1 y " + properties.maxVariantsPerRequest() + " variantes por vez");
        }
        List<Exercise> targets = exercises.findByTypeAndActiveTrue(type);
        if (targets.isEmpty()) {
            throw new FieldValidationException("exerciseType", "No hay ejercicios activos de ese tipo");
        }

        List<GenerateContentResponse.Created> created = new ArrayList<>();
        int rejected = 0;
        int calls = 0;
        String stopped = null;
        outer:
        for (int i = 0; i < count; i++) {
            for (Exercise exercise : targets) {
                List<Existing> existing = existingOf(exercise.getId(), type);
                AiRequest request = ContentPrompts.forType(type,
                        existing.stream().map(Existing::theme).distinct().toList(), properties.maxTokens());
                AiResponse response;
                try {
                    response = ai.generate(AiPurpose.CONTENT_GENERATION, request);
                } catch (AiUnavailableException e) {
                    if (calls == 0) {
                        throw unavailable(e);
                    }
                    stopped = e.reason().name();
                    break outer;
                }
                calls++;
                try {
                    Candidate candidate = GeneratedContentValidator.validate(type, parse(response.text()), existing);
                    created.add(save(exercise, candidate));
                } catch (InvalidContentException | JsonProcessingException e) {
                    rejected++;
                    // Sin el texto de la IA: solo el motivo.
                    log.info("Contenido generado descartado: {}", e.getMessage());
                }
            }
        }
        log.info("Generación de contenido: tipo={} pedidas={} creadas={} descartadas={} cortada={}", type, calls,
                created.size(), rejected, stopped);
        return new GenerateContentResponse(count * targets.size(), created, rejected, stopped);
    }

    private GenerateContentResponse.Created save(Exercise exercise, Candidate candidate) {
        String key = variantKey(candidate.theme());
        variants.save(new ExerciseVariant(UUID.randomUUID(), exercise.getId(), key, candidate.content(),
                properties.activateGenerated(), VariantSource.AI));
        return new GenerateContentResponse.Created(exercise.getCode(), key, candidate.theme(),
                properties.activateGenerated());
    }

    private List<Existing> existingOf(UUID exerciseId, ExerciseType type) {
        String field = GeneratedContentValidator.listField(type);
        List<Existing> result = new ArrayList<>();
        for (ExerciseVariant variant : variants.findByExerciseId(exerciseId)) {
            Map<String, Object> content = variant.getContent();
            Object theme = content.get("theme");
            Set<String> elements = new HashSet<>();
            if (content.get(field) instanceof List<?> list) {
                list.forEach(item -> elements.add(String.valueOf(item)));
            }
            result.add(new Existing(theme instanceof String text ? text : variant.getVariantKey(), elements));
        }
        return result;
    }

    /** La IA a veces envuelve el JSON en un bloque de código o le agrega texto: se toma lo que está entre llaves. */
    private JsonNode parse(String text) throws JsonProcessingException {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidContentException("La respuesta no tiene un objeto JSON");
        }
        return mapper.readTree(text.substring(start, end + 1));
    }

    /** {@code ia-<tema>-<5 al azar>}: reconocible, único por ejercicio y de hasta 37 caracteres (el límite es 40). */
    static String variantKey(String theme) {
        String slug = Normalizer.normalize(theme, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 28) {
            slug = slug.substring(0, 28).replaceAll("-$", "");
        }
        return "ia-" + slug + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    }

    private static ApiException unavailable(AiUnavailableException e) {
        String detail = switch (e.reason()) {
            case DISABLED -> "La IA está desactivada en el servidor.";
            case LIMIT_EXCEEDED -> "Se alcanzó el límite de gasto de IA.";
            case CIRCUIT_OPEN -> "El proveedor de IA está pausado por fallas recientes. Probá de nuevo en un minuto.";
            case PROVIDER_ERROR -> "El proveedor de IA falló.";
        };
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE, detail);
    }
}
