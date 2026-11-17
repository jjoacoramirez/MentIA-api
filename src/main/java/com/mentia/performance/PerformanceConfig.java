package com.mentia.performance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mentia.engine.EngineProperties;

@Configuration
@EnableConfigurationProperties(AssessmentProperties.class)
public class PerformanceConfig {

    @Bean
    InitialLevelPolicy initialLevelPolicy(AssessmentProperties assessment, EngineProperties engine) {
        return new InitialLevelPolicy(assessment, engine);
    }
}
