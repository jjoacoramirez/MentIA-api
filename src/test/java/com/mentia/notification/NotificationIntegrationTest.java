package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.common.error.FieldValidationException;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

@Transactional
@TestPropertySource(properties = {
        "mentia.notification.enabled=true",
        "mentia.notification.vapid-public-key=" + PushTestKeys.PUBLIC,
        "mentia.notification.vapid-private-key=" + PushTestKeys.PRIVATE,
        "mentia.notification.subject=" + PushTestKeys.SUBJECT,
        "mentia.notification.min-streak=1",
        "mentia.notification.max-subscriptions-per-user=2" })
class NotificationIntegrationTest extends IntegrationTest {

    @Autowired NotificationService service;
    @Autowired StreakReminderJob job;
    @Autowired PushTestService pushTest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApiClient api;
    @Autowired Clock clock;
    @Autowired EntityManager entityManager;
    @MockitoBean PushSender sender;

    private static String endpoint(String id) {
        return "https://fcm.googleapis.com/fcm/send/" + id;
    }

    /** El test corre en una sola transacción: hay que volcar los cambios de JPA antes de consultar con JDBC. */
    private int subscriptionsOf(UUID user) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE user_id = ?", Integer.class, user);
    }

    @Test
    void subscribingTwiceKeepsASingleSubscription() throws Exception {
        var user = api.newUser();
        service.subscribe(user.id(), endpoint("a"));
        service.subscribe(user.id(), endpoint("a"));
        assertThat(subscriptionsOf(user.id())).isEqualTo(1);
    }

    @Test
    void existingSubscriptionsReceiveKeysWithoutCreatingAnotherDevice() throws Exception {
        var user = api.newUser();
        String url = endpoint("keys");
        service.subscribe(user.id(), url);
        service.subscribe(user.id(), url, WebPushEncryptionTest.RECEIVER, WebPushEncryptionTest.AUTH);
        assertThat(subscriptionsOf(user.id())).isEqualTo(1);
        var device = service.devicesOf(user.id()).getFirst();
        assertThat(device.p256dh()).isEqualTo(WebPushEncryptionTest.RECEIVER);
        assertThat(device.auth()).isEqualTo(WebPushEncryptionTest.AUTH);
        // Un cliente anterior no borra las claves ya registradas.
        service.subscribe(user.id(), url);
        assertThat(service.devicesOf(user.id()).getFirst().auth()).isEqualTo(WebPushEncryptionTest.AUTH);
    }

    @Test
    void theSameBrowserFollowsTheLastAccountThatRegisteredIt() throws Exception {
        var first = api.newUser();
        var second = api.newUser();
        service.subscribe(first.id(), endpoint("shared"));
        service.subscribe(second.id(), endpoint("shared"));
        assertThat(subscriptionsOf(first.id())).isZero();
        assertThat(subscriptionsOf(second.id())).isEqualTo(1);
    }

    @Test
    void keepsOnlyTheNewestSubscriptionsPerUser() throws Exception {
        var user = api.newUser();
        service.subscribe(user.id(), endpoint("1"));
        service.subscribe(user.id(), endpoint("2"));
        service.subscribe(user.id(), endpoint("3"));
        assertThat(subscriptionsOf(user.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE endpoint = ?", Integer.class,
                endpoint("3"))).isEqualTo(1);
    }

    @Test
    void rejectsEndpointsOutsideTheKnownPushServices() throws Exception {
        var user = api.newUser();
        assertThatThrownBy(() -> service.subscribe(user.id(), "https://evil.example/x"))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.subscribe(user.id(), "http://fcm.googleapis.com/x"))
                .isInstanceOf(FieldValidationException.class);
        assertThat(subscriptionsOf(user.id())).isZero();
    }

    @Test
    void unsubscribingOnlyDeletesYourOwnSubscription() throws Exception {
        var owner = api.newUser();
        var other = api.newUser();
        service.subscribe(owner.id(), endpoint("mine"));
        service.unsubscribe(other.id(), endpoint("mine"));
        assertThat(subscriptionsOf(owner.id())).isEqualTo(1);
        service.unsubscribe(owner.id(), endpoint("mine"));
        assertThat(subscriptionsOf(owner.id())).isZero();
    }

    @Test
    void deletingTheUserDeletesTheirSubscriptions() throws Exception {
        var user = api.newUser();
        service.subscribe(user.id(), endpoint("cascade"));
        jdbc.update("DELETE FROM users WHERE id = ?", user.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE endpoint = ?", Integer.class,
                endpoint("cascade"))).isZero();
    }

    @Test
    void remindsOnlyWhoHasAStreakAndHasNotTrainedTodayAndOnlyOncePerDay() throws Exception {
        var atRisk = api.newUser();
        var trainedToday = api.newUser();
        var noStreak = api.newUser();
        service.subscribe(atRisk.id(), endpoint("risk"));
        service.subscribe(trainedToday.id(), endpoint("done"));
        service.subscribe(noStreak.id(), endpoint("none"));
        // Hora de Buenos Aires: mediodía de hoy y de ayer, sin depender de la hora a la que corre el test.
        var zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        completedSession(atRisk.id(), today.minusDays(1).atTime(12, 0).atZone(zone).toOffsetDateTime());
        completedSession(trainedToday.id(), today.minusDays(1).atTime(12, 0).atZone(zone).toOffsetDateTime());
        completedSession(trainedToday.id(), today.atTime(0, 1).atZone(zone).toOffsetDateTime());
        given(sender.send(any())).willReturn(PushSender.Result.SENT);

        assertThat(job.run(today)).isEqualTo(1);
        verify(sender).send(endpoint("risk"));
        verify(sender, never()).send(endpoint("done"));
        verify(sender, never()).send(endpoint("none"));

        // Una segunda corrida el mismo día no vuelve a avisar.
        assertThat(job.run(today)).isZero();
    }

    @Test
    void aGoneSubscriptionIsDeletedWhenTheServiceSaysSo() throws Exception {
        var user = api.newUser();
        service.subscribe(user.id(), endpoint("gone"));
        var zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        completedSession(user.id(), today.minusDays(1).atTime(12, 0).atZone(zone).toOffsetDateTime());
        given(sender.send(any())).willReturn(PushSender.Result.GONE);

        assertThat(job.run(today)).isZero();
        assertThat(subscriptionsOf(user.id())).isZero();
    }

    @Test
    void adminTestPushReachesEveryDeviceWithoutStreakAndWithoutUsingUpTheDay() throws Exception {
        var user = api.newUser();
        service.subscribe(user.id(), endpoint("t1"));
        service.subscribe(user.id(), endpoint("t2"));
        given(sender.send(endpoint("t1"))).willReturn(PushSender.Result.SENT);
        given(sender.send(endpoint("t2"))).willReturn(PushSender.Result.GONE);

        // Sin ninguna sesión (sin racha) igual se manda: es una prueba.
        var result = pushTest.send(user.username().toUpperCase());

        assertThat(result).isEqualTo(new com.mentia.notification.dto.TestPushResponse(2, 1, 1, 0));
        assertThat(subscriptionsOf(user.id())).isEqualTo(1);
        // La prueba no consume el recordatorio del día.
        assertThat(jdbc.queryForObject("SELECT last_reminded_on FROM push_subscriptions WHERE user_id = ?",
                java.sql.Date.class, user.id())).isNull();
    }

    private void completedSession(UUID user, OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO training_sessions(id, user_id, type, status, planned_minutes, created_at, started_at, finished_at)
                VALUES (?, ?, 'TRAINING', 'COMPLETED', 5, ?, ?, ?)
                """, UUID.randomUUID(), user, at, at, at);
    }
}
