package lt.vsh1ft.groundstation.tle;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class CelestrakClient {

    private final RestClient celestrakRestClient;

    public StoredOmm latest(NoradId satellite) {
        return fetch(satellite).stream()
                .findFirst()
                .orElseThrow(() -> new SatelliteNotFoundException(satellite));
    }

    private List<StoredOmm> fetch(NoradId satellite) {
        try {
            final var response = celestrakRestClient.get()
                    .uri("/NORAD/elements/gp.php?CATNR={id}&FORMAT=JSON", satellite.value())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<StoredOmm>>() {});
            return response == null ? List.of() : response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new SatelliteNotFoundException(satellite, e);
        } catch (RestClientException e) {
            throw new CelestrakException("CelesTrak request for " + satellite + " failed: " + e.getMessage(), e);
        }
    }
}
