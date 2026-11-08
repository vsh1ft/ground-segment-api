package lt.vsh1ft.groundstation.alarm;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmArchive alarms;
    private final Clock clock;

    public AlarmsResponse open(NoradId satellite) {
        return new AlarmsResponse(alarms.open(satellite).stream().map(AlarmResponse::of).toList());
    }

    public AlarmResponse acknowledge(NoradId satellite, long id) {
        final var alarm = alarms.find(satellite, id).orElseThrow(() -> new UnknownAlarmException(satellite, id));
        final var acknowledged = alarm.acknowledge(clock.instant());
        if (acknowledged.equals(alarm)) {
            return AlarmResponse.of(alarm);
        }
        try {
            return AlarmResponse.of(alarms.save(acknowledged));
        } catch (OptimisticLockingFailureException e) {
            throw new AlarmChangedException(id);
        }
    }
}
