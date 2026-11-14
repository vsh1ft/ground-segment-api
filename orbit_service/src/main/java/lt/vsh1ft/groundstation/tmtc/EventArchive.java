package lt.vsh1ft.groundstation.tmtc;


import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.CursorPage;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EventArchive {

    private final JdbcAggregateOperations aggregates;

    void store(StoredEvent event) {
        aggregates.insert(event);
    }

    CursorPage<StoredEvent> events(NoradId satellite, EventsQuery query) {
        final var newestFirst = Query.query(matching(satellite, query))
                .sort(Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.limit() + 1);
        return CursorPage.of(aggregates.findAll(newestFirst, StoredEvent.class), query.limit(), StoredEvent::id);
    }

    private static Criteria matching(NoradId satellite, EventsQuery query) {
        var criteria = Criteria.where("noradId").is(satellite.value());
        if (query.before() != null) {
            criteria = criteria.and("id").lessThan(query.before());
        }
        if (query.from() != null) {
            criteria = criteria.and("generationTime").greaterThanOrEquals(query.from());
        }
        if (query.to() != null) {
            criteria = criteria.and("generationTime").lessThan(query.to());
        }
        if (query.minSeverity() != null) {
            criteria = criteria.and("severity").in(query.minSeverity().andWorse().stream().map(Enum::name).toList());
        }
        return criteria;
    }
}
