package com.mentia.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Costo en micro-USD con los tokens reales que devuelve la API.
 *
 * <p>Como el precio está en USD por millón de tokens, {@code tokens × precio} da directamente micro-USD
 * (1 USD = 10^6 micro-USD). Se redondea hacia arriba: ante la duda, se cuenta de más.
 */
public class AiCostCalculator {

    private final AiProperties.Pricing pricing;

    public AiCostCalculator(AiProperties.Pricing pricing) {
        this.pricing = pricing;
    }

    public long costMicroUsd(int tokensIn, int tokensOut) {
        BigDecimal cost = pricing.inputUsdPerMillion().multiply(BigDecimal.valueOf(tokensIn))
                .add(pricing.outputUsdPerMillion().multiply(BigDecimal.valueOf(tokensOut)));
        return cost.setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
