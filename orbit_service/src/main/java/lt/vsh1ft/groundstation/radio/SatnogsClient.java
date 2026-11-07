package lt.vsh1ft.groundstation.radio;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SatnogsClient {

    private static final ParameterizedTypeReference<List<SatnogsTransmitter>> TRANSMITTERS = new ParameterizedTypeReference<>() {};

    private final RestClient satnogsRestClient;

    public List<SatnogsTransmitter> transmitters() {
        final var response = satnogsRestClient.get()
                .uri("/api/transmitters/?format=json")
                .retrieve()
                .body(TRANSMITTERS);
        return response == null ? List.of() : response;
    }
}
