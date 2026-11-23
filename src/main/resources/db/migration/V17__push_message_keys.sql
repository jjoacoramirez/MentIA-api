-- Claves de la suscripción para enviar el tipo de recordatorio cifrado (RFC 8291).
-- Las suscripciones anteriores se completan al volver a abrir MentIA, sin pedir permiso otra vez.
ALTER TABLE push_subscriptions ADD COLUMN p256dh VARCHAR(128);
ALTER TABLE push_subscriptions ADD COLUMN auth_secret VARCHAR(64);
