package com.sprint.bank.exception;

import com.sprint.bank.auth.exception.AccountLockedException;
import com.sprint.bank.auth.exception.InvalidCredentialsException;
import com.sprint.bank.auth.exception.InvalidTokenException;
import com.sprint.bank.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException exception, WebRequest request) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        String firstMsg = errors.values().stream().findFirst().orElse("Validation failed.");
        return buildResponse(HttpStatus.BAD_REQUEST, firstMsg, request, errors);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public Object handleDuplicateEmail(DuplicateEmailException exception, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleConflict(DataIntegrityViolationException exception, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Operation conflicts with existing data.", request, Map.of());
    }

    @ExceptionHandler({BadCredentialsException.class, InvalidCredentialsException.class})
    public Object handleBadCredentials(RuntimeException exception, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password.", request, Map.of());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public Object handleInvalidToken(InvalidTokenException exception, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccountLockedException.class)
    public Object handleAccountLocked(AccountLockedException exception, WebRequest request) {
        return buildResponse(HttpStatus.LOCKED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({AccessDeniedException.class, ResourceForbiddenException.class})
    public Object handleForbidden(RuntimeException exception, WebRequest request) {
        String message = exception instanceof ResourceForbiddenException
                ? exception.getMessage()
                : "You are not allowed to access this resource.";
        return buildResponse(HttpStatus.FORBIDDEN, message, request, Map.of());
    }

    @ExceptionHandler({AccountNotFoundException.class, ResourceNotFoundException.class})
    public Object handleNotFound(RuntimeException exception, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleMissingEndpoint(NoResourceFoundException exception, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource was not found.", request, Map.of());
    }

    @ExceptionHandler({InsufficientBalanceException.class, InvalidAccountStatusException.class, IllegalArgumentException.class})
    public Object handleBankingBadRequest(RuntimeException exception, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Object handleBadParams(Exception exception, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid or missing request parameter.", request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleMalformedJson(HttpMessageNotReadableException exception, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, WebRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this endpoint.", request, Map.of());
    }

    @ExceptionHandler(EmailDeliveryUnavailableException.class)
    public Object handleEmailUnavailable(EmailDeliveryUnavailableException exception, WebRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is temporarily unavailable.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception exception, WebRequest request) {
        logger.error("Unexpected error processing request", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred.", request, Map.of());
    }

    private Object buildResponse(
            HttpStatus status,
            String message,
            WebRequest request,
            Map<String, String> validationErrors) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        String accept = ((ServletWebRequest) request).getRequest().getHeader("Accept");
        boolean isApi = path.startsWith("/api/")
                || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));

        if (!isApi) {
            ModelAndView mv = new ModelAndView("error");
            mv.setStatus(status);
            mv.addObject("status", status.value());
            mv.addObject("error", status.getReasonPhrase());
            mv.addObject("message", message);
            mv.addObject("path", path);
            return mv;
        }

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                validationErrors
        );
        return ResponseEntity.status(status).body(error);
    }
}
