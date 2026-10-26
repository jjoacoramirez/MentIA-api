package com.mentia.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio que se traduce a un ProblemDetail con el {@link ErrorCode} indicado.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
