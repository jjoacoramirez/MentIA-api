package com.mentia.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.mentia.progress.ProgressService;
import com.mentia.progress.ProgressService.StreakStatus;

/**
 * Una vez por día avisa a quien todavía no entrenó hoy, incluso sin racha cuando min-streak es 0.
 * Como mucho un aviso por suscripción por día
 * ({@code last_reminded_on}), así un reinicio o una corrida repetida no manda de más.
 */
public class StreakReminderJob {

    private static final Logger log = LoggerFactory.getLogger(StreakReminderJob.class);

    private final NotificationService notifications;
    private final ProgressService progress;
    private final PushSender sender;
    private final NotificationProperties properties;
    private final Clock clock;

    public StreakReminderJob(NotificationService notifications, ProgressService progress, PushSender sender,
            NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.progress = progress;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${mentia.notification.reminder-cron}", zone = "${mentia.progress.time-zone}")
    public void run() {
        run(LocalDate.now(clock.withZone(progress.timeZone())));
    }

    /** Devuelve cuántos avisos se mandaron. */
    int run(LocalDate today) {
        Map<UUID, Boolean> atRisk = new HashMap<>();
        int sent = 0;
        for (NotificationService.Due due : notifications.due(today)) {
            boolean send = atRisk.computeIfAbsent(due.userId(), id -> {
                StreakStatus status = progress.streakStatus(id);
                return status.streak() >= properties.minStreak() && !status.trainedToday();
            });
            if (!send) {
                continue;
            }
            switch (sender.send(due.endpoint())) {
                case SENT -> {
                    notifications.markReminded(due.subscriptionId(), today);
                    sent++;
                }
                case GONE -> notifications.remove(due.subscriptionId());
                case FAILED, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, PROVIDER_UNAVAILABLE, REJECTED, CONNECTION_FAILED -> { }
            }
        }
        log.info("Recordatorios de entrenamiento enviados: {}", sent);
        return sent;
    }
}
