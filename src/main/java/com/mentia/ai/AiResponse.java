package com.mentia.ai;

/** Texto generado y tokens reales que informó el proveedor (base del cálculo de costo). */
public record AiResponse(String text, int tokensIn, int tokensOut) {

    /** Sin el contenido, para no filtrarlo a los logs. */
    @Override
    public String toString() {
        return "AiResponse[chars=" + text.length() + ", tokensIn=" + tokensIn + ", tokensOut=" + tokensOut + "]";
    }
}
