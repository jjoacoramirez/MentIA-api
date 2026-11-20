package com.mentia.notification;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mentia.auth.AuthService;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.notification.dto.TestPushResponse;

/**
 * Herramienta de debug para admins: manda ya el aviso a los dispositivos de una cuenta, sin mirar la racha ni el
 * límite de uno por día, y sin marcar el día como recordado. No devuelve ni loguea las URLs de suscripción.
 */
@Service
public class PushTestService {

    private static final Logger log = LoggerFactory.getLogger(PushTestService.class);

    private final AuthService authService;
    private final NotificationService notifications;
    private final PushSender sender;
    private final NotificationProperties properties;

    public PushTestService(AuthService authService, NotificationService notifications, PushSender sender,
            NotificationProperties properties) {
        this.authService = authService;
        this.notifications = notifications;
        this.sender = sender;
        this.properties = properties;
    }

    /** Sin transacción: mientras se espera al servicio de push no se mantiene abierta una conexión a la base. */
    public TestPushResponse send(String username) {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PUSH_DISABLED,
                    "Los recordatorios no están habilitados en el servidor.");
        }
        UUID userId = authService.findUserId(username).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ErrorCode.USER_NOT_FOUND, "No existe una cuenta con ese alias."));
        List<NotificationService.Device> devices = notifications.devicesOf(userId);
        int sent = 0;
        int removed = 0;
        int failed = 0;
        Map<String, Integer> failures = new LinkedHashMap<>();
        for (NotificationService.Device device : devices) {
            PushSender.Result result = sender.send(device.endpoint());
            switch (result) {
                case SENT -> sent++;
                case GONE -> {
                    notifications.remove(device.subscriptionId());
                    removed++;
                }
                case FAILED, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, PROVIDER_UNAVAILABLE, REJECTED, CONNECTION_FAILED -> {
                    failed++;
                    failures.merge(result.name(), 1, Integer::sum);
                }
            }
        }
        log.info("Aviso de prueba: dispositivos={} enviados={} eliminados={} fallidos={}", devices.size(), sent,
                removed, failed);
        return new TestPushResponse(devices.size(), sent, removed, failed, failures);
    }
}
