package lt.vsh1ft.groundstation.api;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import lt.vsh1ft.groundstation.pass.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SatelliteNotFoundException.class)
    public ProblemDetail notFound(SatelliteNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrbitDataUnavailableException.class)
    public ProblemDetail unavailable(OrbitDataUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        final var detail = e.getParameterValidationResults().stream()
                .flatMap(ApiExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(e, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    private static Stream<String> describe(ParameterValidationResult result) {
        final var parameter = requestName(result.getMethodParameter());
        return result.getResolvableErrors().stream().map(error -> parameter + ": " + error.getDefaultMessage());
    }

    private static String requestName(MethodParameter parameter) {
        final var requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        return parameter.getParameterName();
    }
}
