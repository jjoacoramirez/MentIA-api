package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.mentia.support.IntegrationTest;

/**
 * Una llamada real a Anthropic con la key de {@code ANTHROPIC_API_KEY}. Cuesta centavos, así que solo corre si además
 * se define {@code MENTIA_AI_SMOKE=true}; en el CI y en un {@code verify} normal queda salteado.
 * Uso: {@code ./mvnw test -Dtest=AnthropicSmokeTest} con las dos variables en la terminal.
 */
@EnabledIfEnvironmentVariable(named = "MENTIA_AI_SMOKE", matches = "true")
@TestPropertySource(properties = "mentia.ai.enabled=true")
class AnthropicSmokeTest extends IntegrationTest {

    @Autowired
    AiClient aiClient;

    @Autowired
    GuardedAiService guardedAiService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void laKeyFuncionaYElUsoQuedaRegistrado() {
        assertThat(aiClient).as("ANTHROPIC_API_KEY tiene que estar definida en esta terminal")
                .isInstanceOf(AnthropicAiClient.class);
        Long before = jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE result = 'OK'", Long.class);

        AiResponse response = guardedAiService.generate(AiPurpose.CONTENT_GENERATION,
                new AiRequest("Respondé solo con lo que se pide, sin explicaciones.",
                        "Escribí tres palabras en español separadas por coma: una fruta, un color y un animal.", 200));

        assertThat(response.text()).as("respuesta vacía: el pensamiento pudo consumir todos los tokens").isNotBlank();
        assertThat(response.tokensIn()).isPositive();
        assertThat(response.tokensOut()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE result = 'OK'", Long.class))
                .isEqualTo(before + 1);
        System.out.println("Respuesta de la IA: " + response.text().strip() + " (" + response + ")");
    }
}
