package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebPushSenderTest {

    private MockRestServiceServer server;
    private WebPushSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new WebPushSender(builder.build(),
                new VapidSigner(PushTestKeys.PUBLIC, PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, Clock.systemUTC()),
                new PushEndpointPolicy(List.of("fcm.googleapis.com")), 21600);
    }

    @Test
    void sendsAnEmptyAuthenticatedPush() {
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("TTL", "21600"))
                .andExpect(header("Authorization", Matchers.startsWith("vapid t=")))
                .andRespond(withSuccess());

        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void goneSubscriptionsAreReportedSoTheyCanBeDeleted() {
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(withStatus(HttpStatus.GONE));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.GONE);

        server.reset();
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.GONE);
    }

    @Test
    void otherFailuresAreRetriedLater() {
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.PROVIDER_UNAVAILABLE);

        server.reset();
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.RATE_LIMITED);
    }

    @ParameterizedTest
    @CsvSource({"401, UNAUTHORIZED", "403, FORBIDDEN", "400, REJECTED", "503, PROVIDER_UNAVAILABLE"})
    void identifiesProviderRejections(int status, PushSender.Result result) {
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(result);
        server.verify();
    }

    @Test
    void identifiesConnectionFailures() {
        server.expect(requestTo(PushTestKeys.ENDPOINT)).andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withException(new java.net.SocketTimeoutException()));
        assertThat(sender.send(PushTestKeys.ENDPOINT)).isEqualTo(PushSender.Result.CONNECTION_FAILED);
        server.verify();
    }

    @Test
    void neverCallsAHostOutsideTheAllowlist() {
        // Sin expectativas registradas: cualquier request real haría fallar el test.
        assertThat(sender.send("https://internal.example/admin")).isEqualTo(PushSender.Result.GONE);
        server.verify();
    }
}
