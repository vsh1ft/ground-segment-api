package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import lombok.RequiredArgsConstructor;
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

    Page events(NoradId satellite, EventsQuery query) {
        final var newestFirst = Query.query(matching(satellite, query))
                .sort(Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.limit() + 1);
        final var found = aggregates.findAll(newestFirst, StoredEvent.class);
        final var page = found.stream().limit(query.limit()).toList();
        return new Page(page, found.size() > query.limit() ? page.getLast().id() : null);
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

    record Page(List<StoredEvent> events, Long nextBefore) {
    }
}
