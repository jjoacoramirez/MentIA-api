package com.mentia.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita el largo en bytes UTF-8. BCrypt solo considera los primeros 72 bytes, así que una contraseña
 * de 72 caracteres con tildes o emojis puede superar ese límite.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Supera el largo máximo permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
