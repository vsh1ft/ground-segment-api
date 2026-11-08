package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TmPacketArchive {

    private final JdbcAggregateOperations aggregates;

    void store(NoradId satellite, TmPacket packet, Instant receivedAt) {
        aggregates.insert(StoredTmPacket.received(satellite, packet, receivedAt));
    }

    TmPacketsResponse packets(NoradId satellite, PacketsQuery query) {
        final var newestFirst = Query.query(matching(satellite, query))
                .sort(Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.limit() + 1);
        final var found = aggregates.findAll(newestFirst, StoredTmPacket.class);
        final var page = found.stream().limit(query.limit()).toList();
        final var nextBefore = found.size() > query.limit() ? page.getLast().id() : null;
        return new TmPacketsResponse(page.stream().map(TmPacketResponse::from).toList(), nextBefore);
    }

    private static Criteria matching(NoradId satellite, PacketsQuery query) {
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
        if (query.apid() != null) {
            criteria = criteria.and("apid").is(query.apid());
        }
        if (query.service() != null) {
            criteria = criteria.and("serviceType").is(query.service());
        }
        if (query.subtype() != null) {
            criteria = criteria.and("messageSubtype").is(query.subtype());
        }
        return criteria;
    }
}
