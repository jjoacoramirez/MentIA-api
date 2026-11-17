package com.mentia.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Separado de {@link SecurityConfig} para que los tests de controllers ({@code @WebMvcTest}) puedan importar
 * la cadena de seguridad sin necesitar el secreto del JWT.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfig {
}
