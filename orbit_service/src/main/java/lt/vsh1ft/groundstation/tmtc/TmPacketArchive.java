package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.contact.Visibility;
import lt.vsh1ft.groundstation.shared.CursorPage;
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

    long store(NoradId satellite, TmPacket packet, Instant receivedAt, Visibility visibility) {
        return aggregates.insert(StoredTmPacket.received(satellite, packet, receivedAt, visibility)).id();
    }

    CursorPage<StoredTmPacket> packets(NoradId satellite, PacketsQuery query) {
        final var newestFirst = Query.query(matching(satellite, query))
                .sort(Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.limit() + 1);
        return CursorPage.of(aggregates.findAll(newestFirst, StoredTmPacket.class), query.limit(),
                StoredTmPacket::id);
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
