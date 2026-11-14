package lt.vsh1ft.groundstation.radio;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransmitterController implements TransmittersApi {

    private final TransmitterService service;

    @Override
    public List<TransmitterResponse> transmitters(int noradId, TransmitterStatus status) {
        return service.transmitters(new NoradId(noradId), status).stream().map(TransmitterResponse::from).toList();
    }
}
