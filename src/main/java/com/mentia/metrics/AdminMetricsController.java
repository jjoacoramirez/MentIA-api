package com.mentia.metrics;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/metrics")
public class AdminMetricsController {
    private final AdminMetricsService metrics;

    public AdminMetricsController(AdminMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public AdminMetricsService.Report report(@RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "") String category, @RequestParam(defaultValue = "") String exercise) {
        if (!List.of(7, 30, 90).contains(days) || !category.matches("[A-Z0-9_]{0,40}")
                || !exercise.matches("[A-Z0-9_]{0,40}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Los filtros no son válidos.");
        }
        return metrics.report(days, category, exercise);
    }
}
