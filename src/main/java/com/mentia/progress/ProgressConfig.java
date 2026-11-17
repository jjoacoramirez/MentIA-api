package com.mentia.progress;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProgressProperties.class)
public class ProgressConfig {
}
