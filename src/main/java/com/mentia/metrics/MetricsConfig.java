package com.mentia.metrics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsConfig {
}
