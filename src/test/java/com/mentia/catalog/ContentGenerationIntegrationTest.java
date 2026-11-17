package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiRequest;
import com.mentia.ai.AiResponse;
import com.mentia.ai.GuardedAiService;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.dto.GenerateContentResponse;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.support.IntegrationTest;

@Transactional
class ContentGenerationIntegrationTest extends IntegrationTest {

    private static final String NATURE = """
            {"theme": "naturaleza", "items": ["🌵","🌲","🌴","🌻","🌷","🍀","🍁","🍄","🌾","🌿","🪴","🌹",
            "🪨","🏔️","🌋","🏝️","🏜️","⛰️","🌊","🔥","💧","❄️","⭐","🌙"]}""";

    @Autowired ContentGenerationService service;
    @Autowired CatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean GuardedAiService ai;

    @Test
    void migrationLabelsTheCuratedVariantsAsSeed() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exercise_variants WHERE source = 'SEED'", Integer.class))
                .isGreaterThan(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exercise_variants WHERE source <> 'SEED'", Integer.class))
                .isZero();
    }

    @Test
    void aGeneratedVariantIsStoredAsAiAndSessionsCanUseIt() {
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class))).willReturn(new AiResponse(NATURE, 300, 200));

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 1);

        // El test corre en una sola transacción: se vuelcan los cambios de JPA antes de consultar con JDBC.
        entityManager.flush();
        assertThat(result.created()).singleElement().satisfies(created -> {
            assertThat(created.variantKey()).startsWith("ia-naturaleza-");
            assertThat(jdbc.queryForObject("SELECT source FROM exercise_variants WHERE variant_key = ?", String.class,
                    created.variantKey())).isEqualTo("AI");
            UUID exercise = jdbc.queryForObject("SELECT id FROM exercises WHERE code = ?", UUID.class,
                    created.exerciseCode());
            // Lo que el motor ve al elegir variantes: ya está entre las activas, con su contenido.
            assertThat(catalog.activeVariants(exercise)).extracting(VariantDefinition::variantKey)
                    .contains(created.variantKey());
            assertThat(catalog.activeVariants(exercise).stream()
                    .filter(v -> v.variantKey().equals(created.variantKey())).findFirst().orElseThrow().content())
                    .containsEntry("theme", "naturaleza");
        });
    }

    @Test
    void theSameThemeTwiceIsNotStoredTwice() {
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class))).willReturn(new AiResponse(NATURE, 300, 200));

        GenerateContentResponse result = service.generate(ExerciseType.VISUAL_RECOGNITION, 2);

        assertThat(result.created()).hasSize(1);
        assertThat(result.rejected()).isEqualTo(1);
    }
}
