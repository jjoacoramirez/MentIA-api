package com.mentia.gamification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GamificationProperties.class)
public class GamificationConfig {

    @Bean
    XpPolicy xpPolicy(GamificationProperties properties) {
        return new XpPolicy(properties);
    }
}
