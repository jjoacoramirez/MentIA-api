package com.mentia.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.metrics.dto.ConsistencyReportResponse;

/** Solo ADMIN (ver SecurityConfig). */
@RestController
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/api/v1/admin/metrics/consistency")
    public ConsistencyReportResponse consistency() {
        return metricsService.consistency();
    }
}
