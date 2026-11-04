package com.mentia.performance.dto;

import java.util.List;

/**
 * Lo que dejó una sesión en una categoría: las precisiones de sus intentos y el nivel y el progreso dentro del
 * nivel con los que sigue.
 */
public record CategoryOutcome(List<Double> accuracies, int newLevel, int newProgress) {
}
