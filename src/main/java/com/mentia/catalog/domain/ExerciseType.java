package com.mentia.catalog.domain;

/**
 * Mecánica del ejercicio. Cada valor corresponde a una mecánica implementada en el front, por eso es un enum:
 * sumar un ejercicio de una mecánica existente es solo una fila, sumar una mecánica nueva requiere código en el front.
 */
public enum ExerciseType {
    VISUAL_RECOGNITION,
    SPATIAL_GRID,
    ODD_ONE_OUT,
    STROOP,
    REVERSE_SEQUENCE,
    N_BACK,
    AUDITORY_SEQUENCE
}
