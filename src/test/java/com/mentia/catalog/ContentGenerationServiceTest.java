package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiRequest;
import com.mentia.ai.AiResponse;
import com.mentia.ai.AiUnavailableException;
import com.mentia.ai.AiUnavailableException.Reason;
import com.mentia.ai.GuardedAiService;
import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.ExerciseVariant;
import com.mentia.catalog.domain.VariantSource;
import com.mentia.catalog.dto.GenerateContentResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.common.error.FieldValidationException;

class ContentGenerationServiceTest {

    private static final String VALID = """
            {"theme": "naturaleza", "items": ["🌵","🌲","🌴","🌻","🌷","🍀","🍁","🍄","🌾","🌿","🪴","🌹",
            "🪨","🏔️","🌋","🏝️","🏜️","⛰️","🌊","🔥","💧","❄️","⭐","🌙"]}""";

    private final GuardedAiService ai = mock(GuardedAiService.class);
    private final ExerciseRepository exercises = mock(ExerciseRepository.class);
    private final ExerciseVariantRepository variants = mock(ExerciseVariantRepository.class);
    private final Exercise exercise = mock(Exercise.class);
    private final UUID exerciseId = UUID.randomUUID();
    private ContentGenerationService service;

    @BeforeEach
    void setUp() {
        given(exercise.getId()).willReturn(exerciseId);
        given(exercise.getCode()).willReturn("RECONOCIMIENTO_VISUAL");
        given(exercises.findByTypeAndActiveTrue(ExerciseType.VISUAL_RECOGNITION)).willReturn(List.of(exercise));
        given(variants.findByExerciseId(exerciseId)).willReturn(List.of(new ExerciseVariant(UUID.randomUUID(), exerciseId,
                "animales", Map.of("theme", "animales", "items", List.of("🐶", "🐱")), true, VariantSource.SEED)));
        service = new ContentGenerationService(ai, exercises, variants, new ObjectMapper(),
                new ContentGenerationProperties(5, 700, true));
    }

    private void aiReturns(String... texts) {
        var stubbing = given(ai.generate(eq(AiPurpose.CONTENT_GENERATION), any(AiRequest.class)));
        AiResponse first = new AiResponse(texts[0], 100, 100);
        AiResponse[] rest = java.util.Arrays.stream(texts).skip(1).map(t -> new AiResponse(t, 100, 100))
                .toArray(AiResponse[]::new);
        stubbing.willReturn(first, rest);
    }

    @Test
    void savesAValidVariantMarkedAsAiAndActive() {
        aiReturns(VALID);

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 1);

        assertThat(result.created()).singleElement().satisfies(created -> {
            assertThat(created.theme()).isEqualTo("naturaleza");
            assertThat(created.variantKey()).startsWith("ia-naturaleza-").hasSizeLessThanOrEqualTo(40);
            assertThat(created.active()).isTrue();
        });
        assertThat(result.rejected()).isZero();
        assertThat(result.stoppedReason()).isNull();
        ArgumentCaptor<ExerciseVariant> saved = ArgumentCaptor.forClass(ExerciseVariant.class);
        verify(variants).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(VariantSource.AI);
        assertThat(saved.getValue().getExerciseId()).isEqualTo(exerciseId);
    }

    @Test
    void theSavedVariantRespectsActivateGenerated() {
        service = new ContentGenerationService(ai, exercises, variants, new ObjectMapper(),
                new ContentGenerationProperties(5, 700, false));
        aiReturns(VALID);

        assertThat(service.generate(ExerciseType.VISUAL_RECOGNITION, 1).created()).singleElement()
                .satisfies(created -> assertThat(created.active()).isFalse());
    }

    @Test
    void parsesJsonWrappedInACodeBlockOrExtraText() {
        aiReturns("Acá va:\n```json\n" + VALID + "\n```\nEspero que sirva.");

        assertThat(service.generate(ExerciseType.VISUAL_RECOGNITION, 1).created()).hasSize(1);
    }

    @Test
    void discardsWhatDoesNotValidateAndCountsIt() {
        aiReturns("no es json", "{\"theme\": \"naturaleza\", \"items\": [\"🌵\"]}", VALID);

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 3);

        assertThat(result.created()).hasSize(1);
        assertThat(result.rejected()).isEqualTo(2);
        verify(variants).save(any());
    }

    @Test
    void thePromptCarriesTheExistingThemesAndNoIdsOrUsers() {
        aiReturns(VALID);
        service.generate(ExerciseType.VISUAL_RECOGNITION, 1);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).generate(eq(AiPurpose.CONTENT_GENERATION), request.capture());
        assertThat(request.getValue().userPrompt()).contains("animales", "24 emojis")
                .doesNotContain(exerciseId.toString());
        assertThat(request.getValue().maxTokens()).isEqualTo(700);
    }

    @Test
    void aSecondRequestInTheSameBatchAvoidsTheThemeJustCreated() {
        aiReturns(VALID);
        // La segunda variante ya conoce a la primera: se pide a la base de nuevo y la lista incluye lo guardado.
        given(variants.findByExerciseId(exerciseId)).willReturn(
                List.of(new ExerciseVariant(UUID.randomUUID(), exerciseId, "animales",
                        Map.of("theme", "animales", "items", List.of("🐶")), true, VariantSource.SEED)),
                List.of(new ExerciseVariant(UUID.randomUUID(), exerciseId, "animales",
                        Map.of("theme", "animales", "items", List.of("🐶")), true, VariantSource.SEED),
                        new ExerciseVariant(UUID.randomUUID(), exerciseId, "ia-naturaleza",
                                Map.of("theme", "naturaleza", "items", List.of("🌵")), true, VariantSource.AI)));

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 2);

        // El mismo tema otra vez se descarta: ya existe.
        assertThat(result.created()).hasSize(1);
        assertThat(result.rejected()).isEqualTo(1);
    }

    @Test
    void ifTheAiIsUnavailableFromTheStartItIs503() {
        given(ai.generate(eq(AiPurpose.CONTENT_GENERATION), any(AiRequest.class)))
                .willThrow(new AiUnavailableException(Reason.DISABLED, "off"));

        assertThatThrownBy(() -> service.generate(ExerciseType.VISUAL_RECOGNITION, 2))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verify(variants, never()).save(any());
    }

    @Test
    void ifItStopsHalfwayItKeepsWhatWasCreatedAndSaysWhy() {
        given(ai.generate(eq(AiPurpose.CONTENT_GENERATION), any(AiRequest.class)))
                .willReturn(new AiResponse(VALID, 1, 1))
                .willThrow(new AiUnavailableException(Reason.LIMIT_EXCEEDED, "tope"));

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 3);

        assertThat(result.created()).hasSize(1);
        assertThat(result.stoppedReason()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void rejectsUnsupportedTypesAndOutOfRangeCounts() {
        assertThatThrownBy(() -> service.generate(ExerciseType.STROOP, 1)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.generate(ExerciseType.VISUAL_RECOGNITION, 0))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.generate(ExerciseType.VISUAL_RECOGNITION, 6))
                .isInstanceOf(FieldValidationException.class);
        given(exercises.findByTypeAndActiveTrue(ExerciseType.N_BACK)).willReturn(List.of());
        assertThatThrownBy(() -> service.generate(ExerciseType.N_BACK, 1)).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void variantKeysAreSlugsThatFitTheColumn() {
        assertThat(ContentGenerationService.variantKey("Selva Tropical Húmeda"))
                .matches("ia-selva-tropical-humeda-[0-9a-f]{5}");
        assertThat(ContentGenerationService.variantKey("a".repeat(40))).hasSizeLessThanOrEqualTo(40);
    }
}
