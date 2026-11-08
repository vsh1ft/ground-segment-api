package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32C;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.GroundStationSource;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ApplicationModuleTest
@RecordApplicationEvents
@Import(PostgresTestcontainer.class)
@TestPropertySource(properties = "groundstation.tmtc.link.host=tm-link.invalid")
class FrameModuleTest {

    private static final NoradId FRONTIERSAT = FrontierSat.SATELLITE;
    private static final Instant HEARD = Instant.parse("2026-11-08T19:03:34Z");
    private static final Instant MEASURED = Instant.parse("2026-11-08T13:51:24.338Z");
    private static final UUID VILNIUS_ID = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final Reception DTEC = Reception.satnogs("Dtec Station", 25.12, 55.377, 32.0, 15386644L, HEARD);
    private static final Reception OWN = Reception.own(VILNIUS_ID, VILNIUS, HEARD.plusMillis(120));

    @Autowired
    private FrameReception reception;

    @Autowired
    private ParameterService parameters;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @MockitoSpyBean
    private FrameArchive archive;

    @MockitoBean
    private ContactWatch contacts;

    @MockitoBean
    private SatelliteService satellites;

    @MockitoBean
    private GroundStationSource stations;

    @BeforeEach
    void noFrames() {
        jdbc.update("DELETE FROM received_frame");
    }

    @Test
    @Requirement({"SAT-020", "SAT-030"})
    void realBeaconIsStoredDecodedOnceAndDatedByTheOnBoardClock() {
        final var outcome = reception.receive(FRONTIERSAT, FrontierSat.beacon(), DTEC);

        assertThat(outcome.newFrame()).isTrue();
        assertThat(outcome.newReception()).isTrue();
        final var frame = archive.frame(outcome.frameId());
        assertThat(frame.container()).isEqualTo("BEACON_EXTENDED_V5");
        assertThat(frame.generationTime()).isEqualTo(MEASURED);
        assertThat(frame.firstReceivedAt()).isEqualTo(HEARD);
        assertThat(frame.crcChecked()).isTrue();
        assertThat(frame.length()).isEqualTo(202);
        assertThat(frame.raw()).startsWith("C2A28A0021435453310100");
        assertThat(frame.receptions()).singleElement().isEqualTo(new FrameResponse.Reception(ReceptionSource.SATNOGS,
                "Dtec Station", null, 25.12, 55.377, 32.0, 15386644L, HEARD));
        final var temperature = parameters.values(FRONTIERSAT, "OBC_TEMPERATURE", new ValuesQuery(null, null, null,
                null)).values();
        assertThat(temperature).singleElement().satisfies(value -> {
            assertThat(value.engineering()).isEqualTo(29.25);
            assertThat(value.generationTime()).isEqualTo(MEASURED);
            assertThat(value.frameId()).isEqualTo(outcome.frameId());
            assertThat(value.packetId()).isNull();
        });
        assertThat(parameters.values(FRONTIERSAT, "END_MESSAGE", new ValuesQuery(null, null, null, null)).values())
                .singleElement().extracting(ParameterValuesResponse.Value::engineering).isEqualTo(" S5");
        assertThat(events.stream(ParameterValuesReceived.class)).singleElement()
                .satisfies(received -> assertThat(received.generationTime()).isEqualTo(MEASURED));
    }

    @Test
    @Requirement({"SAT-020", "SAT-050"})
    void sameFrameFromYourOwnStationAddsOnlyAReception() {
        final var first = reception.receive(FRONTIERSAT, FrontierSat.beacon(), DTEC);

        final var second = reception.receive(FRONTIERSAT, FrontierSat.beacon(), OWN);
        final var again = reception.receive(FRONTIERSAT, FrontierSat.beacon(), OWN);

        assertThat(second).isEqualTo(new FrameReception.Outcome(first.frameId(), false, true));
        assertThat(again).isEqualTo(new FrameReception.Outcome(first.frameId(), false, false));
        assertThat(archive.frame(first.frameId()).receptions())
                .extracting(FrameResponse.Reception::source, FrameResponse.Reception::station,
                        FrameResponse.Reception::stationId)
                .containsExactly(tuple(ReceptionSource.SATNOGS, "Dtec Station", null),
                        tuple(ReceptionSource.OWN, "Vilnius", VILNIUS_ID));
        assertThat(parameters.values(FRONTIERSAT, "OBC_TEMPERATURE", new ValuesQuery(null, null, null, null))
                .values()).hasSize(1);
        assertThat(events.stream(ParameterValuesReceived.class)).hasSize(1);
    }

    @Test
    @Requirement("SAT-020")
    void beaconWhoseCrcAStationStrippedIsDecodedUncheckedAndMergesWithTheFullFrame() {
        final var stripped = Arrays.copyOf(FrontierSat.beacon(), 202);

        final var first = reception.receive(FRONTIERSAT, stripped, OWN);
        final var full = reception.receive(FRONTIERSAT, FrontierSat.beacon(), DTEC);

        final var frame = archive.frame(first.frameId());
        assertThat(frame.crcChecked()).isFalse();
        assertThat(frame.container()).isEqualTo("BEACON_EXTENDED_V5");
        assertThat(frame.generationTime()).isEqualTo(MEASURED);
        assertThat(full).isEqualTo(new FrameReception.Outcome(first.frameId(), false, true));
        assertThat(frame.receptions()).extracting(FrameResponse.Reception::source)
                .containsExactly(ReceptionSource.SATNOGS, ReceptionSource.OWN);
        assertThat(events.stream(ParameterValuesReceived.class)).hasSize(1);
    }

    @Test
    @Requirement("SAT-020")
    void frameOfASatelliteWithoutAMissionDatabaseIsStoredWithoutCheckOrDecoding() {
        final var frame = archive.frame(reception.receive(new NoradId(25544), new byte[] {1, 2, 3}, DTEC).frameId());

        assertThat(frame.crcChecked()).isNull();
        assertThat(frame.container()).isNull();
    }

    @Test
    @Requirement("SAT-020")
    void framesThatAreNotACompleteBeaconAreStoredButNotDecoded() {
        final var truncated = withCrc(Arrays.copyOf(FrontierSat.beacon(), 30));
        final var gnss = Arrays.copyOf(FrontierSat.beacon(), 60);
        gnss[4] = 0x30;

        final var short1 = archive.frame(reception.receive(FRONTIERSAT, truncated, DTEC).frameId());
        final var gnss1 = archive.frame(reception.receive(FRONTIERSAT, withCrc(gnss), DTEC).frameId());

        assertThat(short1.crcChecked()).isTrue();
        assertThat(short1.container()).isNull();
        assertThat(gnss1.container()).isNull();
        assertThat(events.stream(ParameterValuesReceived.class)).isEmpty();
    }

    @Test
    @Requirement("SAT-020")
    void frameStoredByAnotherReceptionInTheMeantimeIsNotDecodedAgain() {
        final var first = reception.receive(FRONTIERSAT, FrontierSat.beacon(), DTEC);
        doReturn(Optional.empty()).doCallRealMethod().when(archive).find(any(), any());

        final var raced = reception.receive(FRONTIERSAT, FrontierSat.beacon(), OWN);

        assertThat(raced).isEqualTo(new FrameReception.Outcome(first.frameId(), false, true));
        assertThat(events.stream(ParameterValuesReceived.class)).hasSize(1);
    }

    @Test
    @Requirement("SAT-040")
    void framesAreListedNewestFirstAndFilteredBySourceAndStation() {
        final var satnogsOnly = reception.receive(FRONTIERSAT, withCrc(new byte[] {1, 1}), DTEC).frameId();
        final var both = reception.receive(FRONTIERSAT, FrontierSat.beacon(), DTEC).frameId();
        reception.receive(FRONTIERSAT, FrontierSat.beacon(), OWN);
        final var ownOnly = reception.receive(FRONTIERSAT, withCrc(new byte[] {2, 2}), OWN).frameId();
        reception.receive(new NoradId(25544), new byte[] {9}, DTEC);

        assertThat(ids(new FramesQuery(null, null, null, null))).containsExactly(ownOnly, both, satnogsOnly);
        assertThat(ids(new FramesQuery(null, null, ReceptionSource.OWN, null))).containsExactly(ownOnly, both);
        assertThat(ids(new FramesQuery(null, null, ReceptionSource.SATNOGS, null))).containsExactly(both,
                satnogsOnly);
        assertThat(ids(new FramesQuery(null, null, null, VILNIUS_ID))).containsExactly(ownOnly, both);
        assertThat(ids(new FramesQuery(null, null, null, UUID.randomUUID()))).isEmpty();
        final var firstPage = archive.frames(FRONTIERSAT, new FramesQuery(2, null, null, null));
        assertThat(firstPage.nextBefore()).isEqualTo(both);
        assertThat(ids(new FramesQuery(2, firstPage.nextBefore(), null, null))).containsExactly(satnogsOnly);
        assertThat(archive.frames(FRONTIERSAT, new FramesQuery(2, firstPage.nextBefore(), null, null)).nextBefore())
                .isNull();
    }

    private List<Long> ids(FramesQuery query) {
        return archive.frames(FRONTIERSAT, query).frames().stream().map(FrameResponse::id).toList();
    }

    private static byte[] withCrc(byte[] content) {
        final var crc = new CRC32C();
        crc.update(content);
        return ByteBuffer.allocate(content.length + 4).put(content).putInt((int) crc.getValue()).array();
    }
}
