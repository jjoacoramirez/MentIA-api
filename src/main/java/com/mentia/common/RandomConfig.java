package com.mentia.common;

import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fuente de aleatoriedad inyectable (selección de variantes, rotación, seeds) para poder testear
 * de forma determinística. No se usa para nada de seguridad: para eso está SecureRandom.
 */
@Configuration
public class RandomConfig {

    @Bean
    public RandomGenerator random() {
        // java.util.Random es thread-safe; las implementaciones nuevas de RandomGenerator no lo son.
        return new Random();
    }
}
