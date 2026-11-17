package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiCostCalculatorTest {

    private final AiCostCalculator calculator = new AiCostCalculator(AiTestProperties.defaults().pricing());

    @Test
    void generarCincuentaPalabrasCuestaFraccionesDeCentavo() {
        // 300 in × 0,10 + 300 out × 0,50 = 30 + 150 = 180 micro-USD (USD 0,00018)
        assertThat(calculator.costMicroUsd(300, 300)).isEqualTo(180);
    }

    @Test
    void unMillonDeTokensCuestaElPrecioPublicado() {
        assertThat(calculator.costMicroUsd(1_000_000, 0)).isEqualTo(100_000);
        assertThat(calculator.costMicroUsd(0, 1_000_000)).isEqualTo(500_000);
    }

    @Test
    void redondeaHaciaArribaYNuncaCuentaCeroSiHuboTokens() {
        // 1 token de entrada = 0,1 micro-USD -> 1
        assertThat(calculator.costMicroUsd(1, 0)).isEqualTo(1);
        assertThat(calculator.costMicroUsd(0, 0)).isZero();
    }
}
