package lt.vsh1ft.groundstation.alarm;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AlarmArchive {

    private final JdbcAggregateOperations aggregates;

    List<Alarm> open(NoradId satellite) {
        final var open = Criteria.where("noradId").is(satellite.value()).and("state").not(AlarmState.CLEARED.name());
        return aggregates.findAll(Query.query(open).sort(Sort.by(Sort.Direction.DESC, "id")), Alarm.class);
    }

    Optional<Alarm> find(NoradId satellite, long id) {
        final var matching = Criteria.where("noradId").is(satellite.value()).and("id").is(id);
        return aggregates.findOne(Query.query(matching), Alarm.class);
    }

    Alarm save(Alarm alarm) {
        return aggregates.save(alarm);
    }
}
