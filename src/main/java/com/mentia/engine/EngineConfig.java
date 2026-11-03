package com.mentia.engine;

import java.util.random.RandomGenerator;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ EngineProperties.class, AdaptationProperties.class, RotationProperties.class })
public class EngineConfig {

    @Bean
    RotationPolicy rotationPolicy(RotationProperties properties, RandomGenerator random) {
        return new RotationPolicy(properties, random);
    }

    @Bean
    DifficultyEngine difficultyEngine(AdaptationProperties adaptation, EngineProperties engine) {
        return new DifficultyEngine(adaptation, engine);
    }
}
