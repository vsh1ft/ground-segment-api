package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.CursorPage;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.FloatValue;
import lt.vsh1ft.xtce.IntegerValue;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.Parameter;
import lt.vsh1ft.xtce.ParameterValue;
import lt.vsh1ft.xtce.StringValue;
import lt.vsh1ft.xtce.TimeValue;
import org.junit.jupiter.api.Test;

class ParameterResponsesTest {

    private static final MissionDatabase DATABASE = SimulatorTelemetry.databases().of(SATELLITE).orElseThrow();
    private static final Parameter APID = DATABASE.parameter("APID").orElseThrow();
    private static final Parameter BATTERY = DATABASE.parameter("BATTERY_VOLTAGE").orElseThrow();
    private static final Parameter MODE = DATABASE.parameter("SPACECRAFT_MODE").orElseThrow();
    private static final Instant GENERATED = Instant.parse("2026-11-08T08:53:41.923Z");

    @Test
    @Requirement("PAR-040")
    void parametersDescribeTheirKindUnitAndLabels() {
        assertThat(ParameterResponse.from(APID)).isEqualTo(new ParameterResponse("APID", "Application process ID",
                ParameterResponse.Kind.INTEGER, null, null));
        assertThat(ParameterResponse.from(BATTERY)).isEqualTo(new ParameterResponse("BATTERY_VOLTAGE",
                "Battery voltage", ParameterResponse.Kind.FLOAT, "V", null));
        assertThat(ParameterResponse.from(MODE)).isEqualTo(new ParameterResponse("SPACECRAFT_MODE",
                "Spacecraft mode", ParameterResponse.Kind.ENUMERATED, null, Map.of(0L, "SAFE", 1L, "NOMINAL")));
    }

    @Test
    @Requirement("PAR-020")
    void engineeringValuesAreStoredAsNumbersOrLabels() {
        assertThat(stored(new ParameterValue(APID, 100, Optional.of(new IntegerValue(100)))))
                .extracting(StoredParameterValue::engineeringNumber, StoredParameterValue::engineeringText)
                .containsExactly(100.0, null);
        assertThat(stored(new ParameterValue(BATTERY, 7411, Optional.of(new FloatValue(7.411)))))
                .extracting(StoredParameterValue::engineeringNumber, StoredParameterValue::engineeringText)
                .containsExactly(7.411, null);
        assertThat(stored(new ParameterValue(MODE, 1, Optional.of(new EnumeratedValue("NOMINAL")))))
                .extracting(StoredParameterValue::engineeringNumber, StoredParameterValue::engineeringText)
                .containsExactly(null, "NOMINAL");
        assertThat(stored(new ParameterValue(MODE, 7, Optional.empty())))
                .extracting(StoredParameterValue::raw, StoredParameterValue::engineeringNumber,
                        StoredParameterValue::engineeringText)
                .containsExactly(7L, null, null);
    }

    @Test
    @Requirement("PAR-050")
    void engineeringValuesAreServedAsWholeNumbersDecimalsOrLabels() {
        final var apid = ParameterValuesResponse.of(APID,
                new CursorPage<>(List.of(row(APID, 100, 100.0, null)), null));
        final var battery = ParameterValuesResponse.of(BATTERY,
                new CursorPage<>(List.of(row(BATTERY, 7411, 7.411, null)), 41L));
        final var mode = ParameterValuesResponse.of(MODE, new CursorPage<>(List.of(row(MODE, 1, null, "NOMINAL"),
                row(MODE, 7, null, null)), null));

        assertThat(apid.values().getFirst().engineering()).isEqualTo(100L);
        assertThat(battery.unit()).isEqualTo("V");
        assertThat(battery.nextBefore()).isEqualTo(41L);
        assertThat(battery.values().getFirst()).isEqualTo(new ParameterValuesResponse.Value(42, GENERATED, 7411,
                7.411, 12L, null));
        assertThat(mode.values()).extracting(ParameterValuesResponse.Value::engineering)
                .containsExactly("NOMINAL", null);
    }

    private static StoredParameterValue stored(ParameterValue value) {
        return StoredParameterValue.of(SATELLITE, TelemetrySource.packet(12), GENERATED, value);
    }

    private static StoredParameterValue row(Parameter parameter, long raw, Double number, String text) {
        return new StoredParameterValue(42L, SATELLITE.value(), 12L, null, parameter.name(), GENERATED, raw, number,
                text);
    }

    @Test
    @Requirement({"PAR-040", "PAR-060"})
    void textAndTimeParametersAreDescribedStoredAndServedAsText() {
        final var frontierSat = FrontierSat.databases().of(FrontierSat.SATELLITE).orElseThrow();
        final var name = frontierSat.parameter("SATELLITE_NAME").orElseThrow();
        final var time = frontierSat.parameter("UNIX_EPOCH_TIME").orElseThrow();
        final var nameValue = StoredParameterValue.of(SATELLITE, TelemetrySource.frame(7), GENERATED, new ParameterValue(name,
                0x43545331L, Optional.of(new StringValue("CTS1"))));
        final var timeValue = StoredParameterValue.of(SATELLITE, TelemetrySource.frame(7), GENERATED, new ParameterValue(time,
                1_794_145_884_338L, Optional.of(new TimeValue(Instant.parse("2026-11-08T13:51:24.338Z")))));

        assertThat(ParameterResponse.from(name).kind()).isEqualTo(ParameterResponse.Kind.STRING);
        assertThat(ParameterResponse.from(time).kind()).isEqualTo(ParameterResponse.Kind.TIME);
        assertThat(nameValue.engineering(name.type())).isEqualTo("CTS1");
        assertThat(timeValue.engineering(time.type())).isEqualTo("2026-11-08T13:51:24.338Z");
        assertThat(timeValue.frameId()).isEqualTo(7L);
        assertThat(timeValue.packetId()).isNull();
    }
}
