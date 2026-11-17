package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mentia.progress.ProgressService;
import com.mentia.progress.ProgressService.StreakStatus;

@ExtendWith(MockitoExtension.class)
class StreakReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Mock NotificationService notifications;
    @Mock ProgressService progress;
    @Mock PushSender sender;
    StreakReminderJob job;

    final UUID user = UUID.randomUUID();
    final UUID subscription = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties(true, PushTestKeys.PUBLIC, PushTestKeys.PRIVATE,
                PushTestKeys.SUBJECT, "0 0 19 * * *", 2, 5, Duration.ofHours(6), Duration.ofSeconds(3),
                Duration.ofSeconds(10), List.of("fcm.googleapis.com"));
        job = new StreakReminderJob(notifications, progress, sender, properties, Clock.systemUTC());
    }

    private void due(UUID... subscriptions) {
        given(notifications.due(TODAY)).willReturn(Arrays.stream(subscriptions)
                .map(id -> new NotificationService.Due(id, user, PushTestKeys.ENDPOINT + id)).toList());
    }

    @Test
    void remindsSomeoneWithAStreakWhoHasNotTrainedToday() {
        due(subscription);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(3, false));
        given(sender.send(any())).willReturn(PushSender.Result.SENT);

        assertThat(job.run(TODAY)).isEqualTo(1);
        verify(notifications).markReminded(subscription, TODAY);
    }

    @Test
    void doesNotRemindSomeoneWhoAlreadyTrainedToday() {
        due(subscription);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(3, true));

        assertThat(job.run(TODAY)).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void doesNotRemindSomeoneBelowTheMinimumStreak() {
        due(subscription);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(1, false));

        assertThat(job.run(TODAY)).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void removesSubscriptionsThatNoLongerExist() {
        due(subscription);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(5, false));
        given(sender.send(any())).willReturn(PushSender.Result.GONE);

        assertThat(job.run(TODAY)).isZero();
        verify(notifications).remove(subscription);
        verify(notifications, never()).markReminded(any(), any());
    }

    @Test
    void aFailureIsRetriedNextRunAndDoesNotMarkTheDay() {
        due(subscription);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(5, false));
        given(sender.send(any())).willReturn(PushSender.Result.FAILED);

        assertThat(job.run(TODAY)).isZero();
        verify(notifications, never()).markReminded(any(), any());
        verify(notifications, never()).remove(any());
    }

    @Test
    void looksUpTheStreakOncePerUserEvenWithSeveralDevices() {
        UUID second = UUID.randomUUID();
        due(subscription, second);
        given(progress.streakStatus(user)).willReturn(new StreakStatus(4, false));
        given(sender.send(any())).willReturn(PushSender.Result.SENT);

        assertThat(job.run(TODAY)).isEqualTo(2);
        verify(progress).streakStatus(user);
    }
}
