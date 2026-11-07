package lt.vsh1ft.groundstation.elements;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class CelestrakClient {

    private static final ParameterizedTypeReference<List<StoredOmm>> OMM_LIST = new ParameterizedTypeReference<>() {};

    private final RestClient celestrakRestClient;

    public StoredOmm latest(NoradId satellite) {
        try {
            return fetch("CATNR", String.valueOf(satellite.value())).stream()
                    .findFirst()
                    .orElseThrow(() -> new SatelliteNotFoundException(satellite));
        } catch (HttpClientErrorException.NotFound e) {
            throw new SatelliteNotFoundException(satellite, e);
        } catch (RestClientException e) {
            throw new CelestrakException("CelesTrak request for " + satellite + " failed: " + e.getMessage(), e);
        }
    }

    public List<StoredOmm> group(String group) {
        try {
            return fetch("GROUP", group);
        } catch (RestClientException e) {
            throw new CelestrakException("CelesTrak request for group " + group + " failed: " + e.getMessage(), e);
        }
    }

    private List<StoredOmm> fetch(String query, String value) {
        final var response = celestrakRestClient.get()
                .uri("/NORAD/elements/gp.php?{query}={value}&FORMAT=JSON", query, value)
                .retrieve()
                .body(OMM_LIST);
        return response == null ? List.of() : response;
    }
}
