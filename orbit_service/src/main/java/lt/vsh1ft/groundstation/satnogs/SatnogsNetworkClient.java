package lt.vsh1ft.groundstation.satnogs;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
class SatnogsNetworkClient {

    private static final ParameterizedTypeReference<List<SatnogsObservation>> OBSERVATIONS =
            new ParameterizedTypeReference<>() {};

    private final RestClient satnogsNetworkRestClient;

    Page observations(NoradId satellite, Instant start, Instant end, String next) {
        final var request = next == null
                ? satnogsNetworkRestClient.get().uri(
                "/api/observations/?norad_cat_id={id}&start={start}&end={end}&format=json", satellite.value(), start,
                end)
                : satnogsNetworkRestClient.get().uri(URI.create(next));
        final var response = retrieve(request);
        final var observations = response.getBody() == null ? List.<SatnogsObservation>of() : response.getBody();
        return new Page(observations, next(response.getHeaders()).orElse(null));
    }

    byte[] download(String url) {
        final var body = satnogsNetworkRestClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
        return body == null ? new byte[0] : body;
    }

    private static ResponseEntity<List<SatnogsObservation>> retrieve(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.retrieve().toEntity(OBSERVATIONS);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new SatnogsThrottledException(retryAfter(e).orElse(null), e);
        }
    }

    private static Optional<Duration> retryAfter(HttpClientErrorException.TooManyRequests e) {
        return Optional.ofNullable(e.getResponseHeaders())
                .map(headers -> headers.getFirst(HttpHeaders.RETRY_AFTER))
                .filter(seconds -> seconds.matches("\\d+"))
                .map(seconds -> Duration.ofSeconds(Long.parseLong(seconds)));
    }

    private static Optional<String> next(HttpHeaders headers) {
        return headers.getOrEmpty(HttpHeaders.LINK).stream()
                .flatMap(link -> Arrays.stream(link.split(",")))
                .filter(part -> part.contains("rel=\"next\""))
                .map(part -> part.substring(part.indexOf('<') + 1, part.indexOf('>')))
                .findFirst();
    }

    record Page(List<SatnogsObservation> observations, String next) {
    }
}
