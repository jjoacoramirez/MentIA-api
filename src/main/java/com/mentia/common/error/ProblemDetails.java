package com.mentia.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Construye ProblemDetail con el campo {@code code} que exige el contrato.
 */
public final class ProblemDetails {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(titleOf(status));
        problem.setProperty(CODE, code.name());
        return problem;
    }

    public static ProblemDetail validation(String detail, List<FieldError> errors) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail);
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    private static String titleOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    public record FieldError(String field, String message) {
    }
}
