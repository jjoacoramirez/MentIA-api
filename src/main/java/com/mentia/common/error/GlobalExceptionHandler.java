package com.mentia.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getCode(), ex.getMessage());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), ex.getStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Ocurrió un error inesperado. Probá de nuevo más tarde.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ProblemDetails.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetails.validation("Hay datos de entrada inválidos.", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getCause() instanceof UnrecognizedPropertyException unknown
                ? List.of(new ProblemDetails.FieldError(unknown.getPropertyName(), "Propiedad no permitida."))
                : List.of();
        ProblemDetail problem = ProblemDetails.validation("El cuerpo de la solicitud no es válido.", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Pasa por acá todo error de Spring MVC: garantiza que el ProblemDetail siempre lleve {@code code}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && (problem.getProperties() == null
                || !problem.getProperties().containsKey(ProblemDetails.CODE))) {
            problem.setProperty(ProblemDetails.CODE, defaultCodeFor(statusCode).name());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ErrorCode defaultCodeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
