package lt.vsh1ft.groundstation.api;

import java.util.Comparator;
import java.util.stream.Stream;

import lt.vsh1ft.groundstation.error.ConflictException;
import lt.vsh1ft.groundstation.error.NotFoundException;
import lt.vsh1ft.groundstation.error.UnavailableException;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnavailableException.class)
    public ProblemDetail unavailable(UnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        addErrors(e.getBody(), e.getFieldErrors().stream()
                .map(error -> new InvalidField(error.getField(), message(error))));
        return super.handleMethodArgumentNotValid(e, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        addErrors(e.getBody(), e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new InvalidField(result.getMethodParameter().getParameterName(), message(error)))));
        return super.handleHandlerMethodValidationException(e, headers, status, request);
    }

    private static void addErrors(ProblemDetail body, Stream<InvalidField> errors) {
        body.setProperty("errors", errors.sorted(Comparator.comparing(InvalidField::field)).toList());
    }

    private String message(MessageSourceResolvable error) {
        final var messages = getMessageSource();
        return messages == null ? error.getDefaultMessage() : messages.getMessage(error, LocaleContextHolder.getLocale());
    }

    record InvalidField(String field, String message) {
    }
}
