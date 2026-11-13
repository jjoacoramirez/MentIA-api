package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cliente contra un servidor simulado: ningún test llama a la API real. */
class AnthropicAiClientTest {

    private static final String OK_BODY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5","stop_reason":"end_turn",
             "content":[{"type":"thinking","thinking":""},{"type":"text","text":"casa, perro, mesa"}],
             "usage":{"input_tokens":120,"output_tokens":45,"extra_field":1}}
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> pauses = new ArrayList<>();
    private final AiRequest request = new AiRequest("Sos un generador de listas.", "Dame 3 palabras", 300);

    private AnthropicAiClient client(int maxRetries) {
        AiProperties props = AiTestProperties.with(AiTestProperties.limits("2", "20", "0.1", 5), maxRetries);
        return new AnthropicAiClient(builder.build(), props, pauses::add);
    }

    @Test
    void enviaElPedidoConLosHeadersYElCuerpoEsperadosYDevuelveSoloElTexto() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key-123"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(300))
                .andExpect(jsonPath("$.system").value("Sos un generador de listas."))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Dame 3 palabras"))
                .andExpect(jsonPath("$.output_config.effort").value("low"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiResponse response = client(2).generate(request);

        assertThat(response.text()).isEqualTo("casa, perro, mesa");
        assertThat(response.tokensIn()).isEqualTo(120);
        assertThat(response.tokensOut()).isEqualTo(45);
        server.verify();
    }

    @Test
    void reintentaConBackoffExponencialAnteErrorDelServidorYTerminaBien() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages")).andRespond(withServerError());
        server.expect(requestTo("https://api.anthropic.test/v1/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiResponse response = client(2).generate(request);

        assertThat(response.text()).isEqualTo("casa, perro, mesa");
        assertThat(pauses).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20));
        server.verify();
    }

    @Test
    void sinReintentosDisponiblesFallaComoTransitoria() {
        // 529 = "overloaded" en la API de Anthropic; no existe en el enum HttpStatus.
        server.expect(requestTo("https://api.anthropic.test/v1/messages")).andRespond(withStatus(HttpStatusCode.valueOf(529)));
        server.expect(requestTo("https://api.anthropic.test/v1/messages")).andRespond(withStatus(HttpStatusCode.valueOf(529)));

        assertThatThrownBy(() -> client(1).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.isTransient()).isTrue();
                    assertThat(e.getMessage()).contains("529");
                });
        server.verify();
    }

    @Test
    void unPedidoRechazadoNoSeReintentaNiCuentaComoTransitorio() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"secreto-que-no-debe-aparecer\"}"));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.isTransient()).isFalse();
                    // El cuerpo de la respuesta nunca viaja en el mensaje.
                    assertThat(e.getMessage()).doesNotContain("secreto").contains("401");
                });
        assertThat(pauses).isEmpty();
        server.verify();
    }

    @Test
    void unTimeoutOErrorDeRedSeTrataComoTransitorioYSeReintenta() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(request -> { throw new IOException("Read timed out"); });
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client(2).generate(request).text()).isEqualTo("casa, perro, mesa");
        assertThat(pauses).hasSize(1);
    }

    @Test
    void unaRespuestaSinTextoEsUnErrorNoTransitorio() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess("""
                        {"stop_reason":"max_tokens","content":[{"type":"thinking","thinking":""}],
                         "usage":{"input_tokens":10,"output_tokens":300}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.isTransient()).isFalse());
    }

    @Test
    void unRechazoDelModeloNoSeReintenta() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andRespond(withSuccess("""
                        {"stop_reason":"refusal","content":[],"usage":{"input_tokens":10,"output_tokens":0}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.isTransient()).isFalse());
        assertThat(pauses).isEmpty();
    }

    @Test
    void sinSystemPromptNoEnviaElCampo() {
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"system\""))))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        client(0).generate(new AiRequest(null, "Dame 3 palabras", 300));
        server.verify();
    }

    @Test
    void lasPropiedadesYElPedidoNoExponenLaKeniElContenidoEnToString() {
        assertThat(AiTestProperties.defaults().toString()).doesNotContain("test-key-123");
        assertThat(request.toString()).doesNotContain("generador").doesNotContain("palabras");
    }
}
