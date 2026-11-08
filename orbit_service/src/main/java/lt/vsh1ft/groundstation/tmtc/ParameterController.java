package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParameterController implements ParametersApi {

    private final ParameterService service;

    @Override
    public List<ParameterResponse> parameters(int noradId) {
        return service.parameters(new NoradId(noradId));
    }

    @Override
    public ParameterValuesResponse values(int noradId, String name, ValuesQuery query) {
        return service.values(new NoradId(noradId), name, query);
    }
}
