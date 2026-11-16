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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cliente contra un servidor simulado: ningún test llama a la API real. */
class OpenAiClientTest {

    private static final String URL = "https://api.openai.test/v1/chat/completions";
    private static final String OK_BODY = """
            {"id":"chatcmpl-1","object":"chat.completion","model":"gpt-4o-mini",
             "choices":[{"index":0,"message":{"role":"assistant","content":"casa, perro, mesa","refusal":null},
                         "finish_reason":"stop"}],
             "usage":{"prompt_tokens":120,"completion_tokens":45,"total_tokens":165}}
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> pauses = new ArrayList<>();
    private final AiRequest request = new AiRequest("Sos un generador de listas.", "Dame 3 palabras", 300);

    private OpenAiClient client(int maxRetries) {
        AiProperties props = AiTestProperties.with(AiProperties.OPENAI, AiTestProperties.limits("2", "20", "0.1", 5),
                maxRetries);
        return new OpenAiClient(builder.build(), props, pauses::add);
    }

    @Test
    void enviaElPedidoConLosHeadersYElCuerpoEsperadosYDevuelveElTexto() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key-123"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.max_completion_tokens").value(300))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Sos un generador de listas."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Dame 3 palabras"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("reasoning_effort"))))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiResponse response = client(2).generate(request);

        assertThat(response.text()).isEqualTo("casa, perro, mesa");
        assertThat(response.tokensIn()).isEqualTo(120);
        assertThat(response.tokensOut()).isEqualTo(45);
        server.verify();
    }

    @Test
    void sinSystemPromptMandaSoloElMensajeDelUsuario() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        client(0).generate(new AiRequest(null, "Dame 3 palabras", 300));
        server.verify();
    }

    @Test
    void reintentaConBackoffExponencialAnteErrorDelServidorYTerminaBien() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client(2).generate(request).text()).isEqualTo("casa, perro, mesa");
        assertThat(pauses).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20));
        server.verify();
    }

    @Test
    void unaKeyInvalidaNoSeReintentaNiExponeElCuerpo() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"secreto-que-no-debe-aparecer\"}"));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.isTransient()).isFalse();
                    assertThat(e.getMessage()).doesNotContain("secreto").contains("401");
                });
        assertThat(pauses).isEmpty();
        server.verify();
    }

    @Test
    void unTimeoutSeTrataComoTransitorioYSeReintenta() {
        server.expect(requestTo(URL)).andRespond(req -> { throw new IOException("Read timed out"); });
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client(2).generate(request).text()).isEqualTo("casa, perro, mesa");
        assertThat(pauses).hasSize(1);
    }

    @Test
    void unaRespuestaSinTextoEsUnErrorNoTransitorio() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":300}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.isTransient()).isFalse();
                    assertThat(e.getMessage()).contains("length");
                });
    }

    @Test
    void unRechazoDelModeloNoSeReintenta() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices":[{"message":{"role":"assistant","content":null,"refusal":"No puedo ayudar con eso."},
                             "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(2).generate(request))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.isTransient()).isFalse());
        assertThat(pauses).isEmpty();
    }
}
