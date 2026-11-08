package lt.vsh1ft.groundstation.shared;

import java.util.Comparator;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

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
                        .map(error -> new InvalidField(field(result, error), message(error)))));
        return super.handleHandlerMethodValidationException(e, headers, status, request);
    }

    private static String field(ParameterValidationResult result, MessageSourceResolvable error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : result.getMethodParameter().getParameterName();
    }

    private static void addErrors(ProblemDetail body, Stream<InvalidField> errors) {
        body.setProperty("errors", errors.sorted(Comparator.comparing(InvalidField::field)).toList());
    }

    private String message(MessageSourceResolvable error) {
        return getMessageSource().getMessage(error, LocaleContextHolder.getLocale());
    }

    record InvalidField(String field, String message) {
    }
}
