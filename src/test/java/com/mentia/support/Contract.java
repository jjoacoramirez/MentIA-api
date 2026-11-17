package com.mentia.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import java.nio.file.Path;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Valida request y response contra openapi.yaml (fuente de verdad del contrato).
 * Usar solo con requests válidos: los inválidos fallan la validación del request a propósito.
 */
public final class Contract {

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(Path.of("openapi.yaml").toAbsolutePath().toUri().toString())
            .withBasePathOverride("/api/v1")
            .build();

    private Contract() {
    }

    public static ResultMatcher matchesContract() {
        return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
    }
}
