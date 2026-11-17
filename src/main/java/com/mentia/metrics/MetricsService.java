package com.mentia.metrics;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.metrics.dto.ConsistencyReportResponse;

/** Métricas internas para admins: solo agregados, nunca datos de un usuario individual. */
@Service
public class MetricsService {

    private final MetricsQueries queries;
    private final MetricsProperties properties;
    private final Clock clock;

    public MetricsService(MetricsQueries queries, MetricsProperties properties, Clock clock) {
        this.queries = queries;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConsistencyReportResponse consistency() {
        return ConsistencyReport.build(
                queries.userActivity(properties.timeZone(), properties.includeAdmins()),
                queries.levelChanges(properties.includeAdmins()),
                properties.consistencyGroups(), properties.timeZone(), Instant.now(clock));
    }
}
