package com.mentia.performance.dto;

import java.util.List;

/**
 * Lo que dejó una sesión en una categoría: las precisiones de sus intentos y el nivel con el que sigue.
 */
public record CategoryOutcome(List<Double> accuracies, int newLevel) {
}
