package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.Parameter;
import lt.vsh1ft.xtce.ParameterValue;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ParameterArchive {

    private final JdbcAggregateOperations aggregates;

    void store(NoradId satellite, long packetId, Instant generationTime, List<ParameterValue> values) {
        aggregates.insertAll(values.stream()
                .map(value -> StoredParameterValue.of(satellite, packetId, generationTime, value))
                .toList());
    }

    ParameterValuesResponse values(NoradId satellite, Parameter parameter, ValuesQuery query) {
        final var newestFirst = Query.query(matching(satellite, parameter, query))
                .sort(Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.limit() + 1);
        final var found = aggregates.findAll(newestFirst, StoredParameterValue.class);
        final var page = found.stream().limit(query.limit()).toList();
        final var nextBefore = found.size() > query.limit() ? page.getLast().id() : null;
        return ParameterValuesResponse.of(parameter, page, nextBefore);
    }

    List<StoredParameterValue> ofPackets(NoradId satellite, Collection<Long> packetIds) {
        final var criteria = Criteria.where("noradId").is(satellite.value()).and("packetId").in(packetIds);
        return aggregates.findAll(Query.query(criteria).sort(Sort.by("id")), StoredParameterValue.class);
    }

    private static Criteria matching(NoradId satellite, Parameter parameter, ValuesQuery query) {
        var criteria = Criteria.where("noradId").is(satellite.value()).and("parameter").is(parameter.name());
        if (query.before() != null) {
            criteria = criteria.and("id").lessThan(query.before());
        }
        if (query.from() != null) {
            criteria = criteria.and("generationTime").greaterThanOrEquals(query.from());
        }
        if (query.to() != null) {
            criteria = criteria.and("generationTime").lessThan(query.to());
        }
        return criteria;
    }
}
