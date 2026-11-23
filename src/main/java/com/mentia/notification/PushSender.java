package com.mentia.notification;

/** Puerto de salida hacia el servicio de push del navegador. */
interface PushSender {

    enum Result {
        /** El servicio de push aceptó el aviso. */
        SENT,
        /** La suscripción ya no existe (404/410): hay que borrarla. */
        GONE,
        /** Falla transitoria o rechazo: se reintenta en la próxima corrida. */
        FAILED,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        PROVIDER_UNAVAILABLE,
        REJECTED,
        CONNECTION_FAILED
    }

    Result send(String endpoint);

    default Result send(String endpoint, String p256dh, String auth, boolean hasStreak) {
        return send(endpoint);
    }
}
