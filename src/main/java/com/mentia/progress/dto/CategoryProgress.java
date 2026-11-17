package com.mentia.progress.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Schema {@code CategoryProgress} del contrato: evolución del usuario consigo mismo en una categoría.
 */
public record CategoryProgress(String category, List<Point> points) {

    public record Point(LocalDate periodStart, double accuracy, double avgDifficultyLevel, int sessions) {
    }
}
