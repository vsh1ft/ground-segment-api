package lt.vsh1ft.groundstation.alarm;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AlarmController implements AlarmsApi {

    private final AlarmService service;

    @Override
    public AlarmsResponse alarms(int noradId) {
        return service.open(new NoradId(noradId));
    }

    @Override
    public AlarmResponse acknowledge(int noradId, long id) {
        return service.acknowledge(new NoradId(noradId), id);
    }
}
