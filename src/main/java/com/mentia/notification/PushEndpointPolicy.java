package com.mentia.notification;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * El servidor hace un POST a la URL que manda el cliente, así que solo se aceptan los servicios de push conocidos
 * (https, sin credenciales ni puerto raro). Evita que alguien use la API para pegarle a otros hosts.
 */
final class PushEndpointPolicy {

    static final int MAX_LENGTH = 2048;

    private final List<String> allowedSuffixes;

    PushEndpointPolicy(List<String> allowedSuffixes) {
        this.allowedSuffixes = allowedSuffixes.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return allowedSuffixes.stream().anyMatch(s -> lower.equals(s) || lower.endsWith("." + s));
    }
}
