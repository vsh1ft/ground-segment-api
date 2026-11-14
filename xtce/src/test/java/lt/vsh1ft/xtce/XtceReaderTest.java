package lt.vsh1ft.xtce;

import static lt.vsh1ft.xtce.Xtce.ID;
import static lt.vsh1ft.xtce.Xtce.ROOT;
import static lt.vsh1ft.xtce.Xtce.UINT8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class XtceReaderTest {

    private static final String CHILD_OF_ROOT = """
            <SequenceContainer name="A">
              <EntryList/>
              <BaseContainer containerRef="ROOT">
                <RestrictionCriteria>%s</RestrictionCriteria>
              </BaseContainer>
            </SequenceContainer>""";

    @Test
    void housekeepingDatabaseIsReadWithItsTypesParametersAndContainerTree() {
        final var database = Xtce.housekeeping();

        assertThat(database.name()).isEqualTo("SIMULATOR");
        assertThat(database.parameters()).hasSize(22);
        assertThat(database.parameter("BATTERY_VOLTAGE").orElseThrow().type()).isEqualTo(new FloatParameterType(
                "battery_voltage_t", "V", new IntegerEncoding(16, false),
                new PolynomialCalibrator(List.of(new PolynomialCalibrator.Term(1, 0.001))),
                new AlarmRanges(new AlarmRange(7.20, true, 7.80, true), new AlarmRange(7.12, true, 7.90, true))));
        assertThat(((FloatParameterType) database.parameter("EVENT_BATTERY_VOLTAGE").orElseThrow().type()).limits())
                .isEqualTo(AlarmRanges.NONE);
        assertThat(database.parameter("PLATFORM_TEMPERATURE").orElseThrow().type().encoding())
                .isEqualTo(new IntegerEncoding(16, true));
        assertThat(database.parameter("SPACECRAFT_MODE").orElseThrow().type()).isEqualTo(new EnumeratedParameterType(
                "mode_t", new IntegerEncoding(8, false), Map.of(0L, "SAFE", 1L, "NOMINAL"), Map.of()));
        assertThat(database.parameter("APID").orElseThrow().type())
                .isEqualTo(new IntegerParameterType("uint11_t", null, new IntegerEncoding(11, false),
                        AlarmRanges.NONE));

        final var root = database.root();
        assertThat(root.name()).isEqualTo("CCSDS_PACKET");
        assertThat(root.isAbstract()).isTrue();
        final var pusTm = root.children().getFirst();
        assertThat(pusTm.container().name()).isEqualTo("PUS_TM");
        assertThat(pusTm.restriction()).extracting(comparison -> comparison.parameter().name())
                .containsExactly("PACKET_TYPE", "SECONDARY_HEADER_FLAG");
        final var report = pusTm.container().children().getFirst().container();
        final var structure1 = report.children().getFirst();
        assertThat(structure1.container().name()).isEqualTo("HK_STRUCTURE_1");
        assertThat(structure1.container().isAbstract()).isFalse();
        assertThat(structure1.restriction()).containsExactly(
                new Comparison(database.parameter("APID").orElseThrow(), new IntegerValue(100), true),
                new Comparison(database.parameter("HK_STRUCTURE_ID").orElseThrow(), new IntegerValue(1), true));
    }

    @Test
    void singleComparisonsOnEnumerationLabelsFloatsAndRawValuesAreSupported() {
        final var database = Xtce.read("""
                        <IntegerParameterType name="uint8_t">
                          <UnitSet/><IntegerDataEncoding encoding="unsigned" sizeInBits="8"
                              byteOrder="mostSignificantByteFirst" bitOrder="mostSignificantBitFirst"/>
                        </IntegerParameterType>
                        <FloatParameterType name="half_t" sizeInBits="64">
                          <IntegerDataEncoding>
                            <DefaultCalibrator name="halve">
                              <PolynomialCalibrator><Term exponent="0" coefficient="0"/>
                                <Term exponent="1" coefficient="0.5"/></PolynomialCalibrator>
                            </DefaultCalibrator>
                          </IntegerDataEncoding>
                        </FloatParameterType>
                        <EnumeratedParameterType name="mode_t">
                          <UnitSet/><IntegerDataEncoding/>
                          <EnumerationList><Enumeration value="1" label="NOMINAL"/></EnumerationList>
                        </EnumeratedParameterType>""",
                """
                        <Parameter name="MODE" parameterTypeRef="mode_t"/>
                        <Parameter name="HALF" parameterTypeRef="half_t"/>
                        <Parameter name="ID" parameterTypeRef="uint8_t"/>""",
                """
                        <SequenceContainer name="ROOT">
                          <EntryList>
                            <ParameterRefEntry parameterRef="MODE" shortDescription="mode"/>
                            <ParameterRefEntry parameterRef="HALF"/>
                            <ParameterRefEntry parameterRef="ID"/>
                          </EntryList>
                        </SequenceContainer>
                        <SequenceContainer name="BY_LABEL"><EntryList/><BaseContainer containerRef="ROOT">
                          <RestrictionCriteria><Comparison parameterRef="MODE" value="NOMINAL"/></RestrictionCriteria>
                        </BaseContainer></SequenceContainer>
                        <SequenceContainer name="BY_FLOAT"><EntryList/><BaseContainer containerRef="BY_LABEL">
                          <RestrictionCriteria><Comparison parameterRef="HALF" value="1.5"/></RestrictionCriteria>
                        </BaseContainer></SequenceContainer>
                        <SequenceContainer name="BY_RAW"><EntryList/><BaseContainer containerRef="BY_FLOAT">
                          <RestrictionCriteria>
                            <Comparison parameterRef="ID" value="7" useCalibratedValue="false" instance="0"
                                        comparisonOperator="=="/>
                          </RestrictionCriteria>
                        </BaseContainer></SequenceContainer>""");

        assertThat(database.decode(new byte[] {1, 3, 7}).container().name()).isEqualTo("BY_RAW");
        assertThat(database.decode(new byte[] {1, 3, 8}).container().name()).isEqualTo("BY_FLOAT");
        assertThat(database.parameter("ID").orElseThrow().type().unit()).isNull();
    }

    @Test
    void staticAlarmRangesAreReadWithOpenAndExclusiveBounds() {
        final var database = Xtce.read("""
                        <IntegerParameterType name="uint8_t"><IntegerDataEncoding/>
                          <DefaultAlarm name="count limits" shortDescription="limits" minViolations="1">
                            <StaticAlarmRanges name="ranges" shortDescription="static" rangeForm="outside">
                              <WarningRange minExclusive="10" maxExclusive="20"/>
                              <CriticalRange minInclusive="5"/>
                            </StaticAlarmRanges>
                          </DefaultAlarm>
                        </IntegerParameterType>
                        <FloatParameterType name="f_t"><IntegerDataEncoding/><DefaultAlarm/></FloatParameterType>""",
                ID + "<Parameter name=\"F\" parameterTypeRef=\"f_t\"/>", ROOT);

        assertThat(((IntegerParameterType) database.parameter("ID").orElseThrow().type()).limits()).isEqualTo(
                new AlarmRanges(new AlarmRange(10, false, 20, false),
                        new AlarmRange(5, true, Double.POSITIVE_INFINITY, true)));
        assertThat(((FloatParameterType) database.parameter("F").orElseThrow().type()).limits())
                .isEqualTo(AlarmRanges.NONE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedOrInconsistent")
    void unsupportedOrInconsistentDatabaseIsRejectedNamingTheProblem(String problem, String xtce, String message) {
        assertThatThrownBy(() -> Xtce.read(xtce)).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessage(message);
    }

    static Stream<Arguments> unsupportedOrInconsistent() {
        return Stream.of(
                arguments("nested space system", Xtce.spaceSystem("<SpaceSystem name=\"SUB\"/>"),
                        "Unsupported XTCE element SpaceSystem 'SUB'"),
                arguments("commands", Xtce.spaceSystem("<CommandMetaData/>"),
                        "Unsupported XTCE element CommandMetaData in SpaceSystem 'TEST'"),
                arguments("message set", Xtce.spaceSystem("""
                                <TelemetryMetaData><MessageSet><Message name="M">
                                  <MatchCriteria><Comparison parameterRef="ID" value="1"/></MatchCriteria>
                                  <ContainerRef containerRef="ROOT"/>
                                </Message></MessageSet></TelemetryMetaData>"""),
                        "Unsupported XTCE element MessageSet in TelemetryMetaData in SpaceSystem 'TEST'"),
                arguments("boolean type", types("<BooleanParameterType name=\"b_t\"/>"),
                        "Unsupported XTCE element BooleanParameterType 'b_t'"),
                arguments("string with only a unit", types("<StringParameterType name=\"s_t\"><UnitSet/>"
                                + "</StringParameterType>"),
                        "StringParameterType 's_t' needs exactly one StringDataEncoding and nothing else"),
                arguments("string without encoding", types("<StringParameterType name=\"s_t\"/>"),
                        "StringParameterType 's_t' needs exactly one StringDataEncoding and nothing else"),
                arguments("UTF-8 string", types(string("UTF-8", "<SizeInBits><Fixed><FixedValue>32</FixedValue>"
                                + "</Fixed></SizeInBits>")),
                        "StringDataEncoding in StringParameterType 's_t' must have encoding=\"US-ASCII\"; other "
                                + "character sets are not supported"),
                arguments("variable string", types(string("US-ASCII", "<Variable maxSizeInBits=\"64\">"
                                + "<DynamicValue><ParameterInstanceRef parameterRef=\"ID\"/></DynamicValue>"
                                + "</Variable>")),
                        "Unsupported XTCE element Variable in StringDataEncoding in StringParameterType 's_t'"),
                arguments("terminated string", types(string("US-ASCII", "<SizeInBits><Fixed><FixedValue>32"
                                + "</FixedValue></Fixed><TerminationChar>00</TerminationChar></SizeInBits>")),
                        "Unsupported XTCE element TerminationChar in SizeInBits in StringDataEncoding in "
                                + "StringParameterType 's_t'"),
                arguments("odd string size", types(string("US-ASCII", "<SizeInBits><Fixed><FixedValue>12"
                                + "</FixedValue></Fixed></SizeInBits>")),
                        "StringDataEncoding in StringParameterType 's_t' is 12 bits; strings of whole bytes up to 64 "
                                + "bits are supported"),
                arguments("long string", types(string("US-ASCII", "<SizeInBits><Fixed><FixedValue>72"
                                + "</FixedValue></Fixed></SizeInBits>")),
                        "StringDataEncoding in StringParameterType 's_t' is 72 bits; strings of whole bytes up to 64 "
                                + "bits are supported"),
                arguments("time without epoch", types("<AbsoluteTimeParameterType name=\"t_t\"><Encoding>"
                                + "<IntegerDataEncoding sizeInBits=\"32\"/></Encoding></AbsoluteTimeParameterType>"),
                        "AbsoluteTimeParameterType 't_t' needs an Encoding and a ReferenceTime with an Epoch"),
                arguments("time without encoding", types("<AbsoluteTimeParameterType name=\"t_t\">"
                                + "<ReferenceTime><Epoch>UNIX</Epoch></ReferenceTime></AbsoluteTimeParameterType>"),
                        "AbsoluteTimeParameterType 't_t' needs an Encoding and a ReferenceTime with an Epoch"),
                arguments("time in days", types(time("units=\"days\"", "<IntegerDataEncoding/>", "UNIX")),
                        "Encoding in AbsoluteTimeParameterType 't_t' has units=\"days\"; only \"seconds\" is "
                                + "supported"),
                arguments("float time", types(time("", "<FloatDataEncoding/>", "UNIX")),
                        "Unsupported XTCE element FloatDataEncoding in Encoding in AbsoluteTimeParameterType 't_t'"),
                arguments("TAI epoch", types(time("", "<IntegerDataEncoding/>", "TAI")),
                        "Epoch in ReferenceTime in AbsoluteTimeParameterType 't_t' is TAI; only a date, a date and "
                                + "time or UNIX are supported"),
                arguments("time offset from a parameter", types("<AbsoluteTimeParameterType name=\"t_t\">"
                                + "<Encoding><IntegerDataEncoding/></Encoding><ReferenceTime><OffsetFrom "
                                + "parameterRef=\"ID\"/></ReferenceTime></AbsoluteTimeParameterType>"),
                        "Unsupported XTCE element OffsetFrom in ReferenceTime in AbsoluteTimeParameterType 't_t'"),
                arguments("time type inheritance", types("<AbsoluteTimeParameterType name=\"t_t\" "
                                + "baseType=\"x\"><Encoding><IntegerDataEncoding/></Encoding><ReferenceTime>"
                                + "<Epoch>UNIX</Epoch></ReferenceTime></AbsoluteTimeParameterType>"),
                        "AbsoluteTimeParameterType 't_t' has attribute baseType, which is not supported"),
                arguments("type inheritance",
                        types("<IntegerParameterType name=\"t\" baseType=\"uint8_t\"><IntegerDataEncoding/>"
                                + "</IntegerParameterType>"),
                        "IntegerParameterType 't' has attribute baseType, which is not supported"),
                arguments("valid range", types("""
                                <IntegerParameterType name="t"><IntegerDataEncoding/>
                                  <ValidRange minInclusive="0" maxInclusive="1"/></IntegerParameterType>"""),
                        "Unsupported XTCE element ValidRange in IntegerParameterType 't'"),
                arguments("no encoding", types("<IntegerParameterType name=\"t\"/>"),
                        "IntegerParameterType 't' has no IntegerDataEncoding"),
                arguments("duplicate type", types(UINT8),
                        "Not a valid XTCE 1.2 document: cvc-identity-constraint.4.2.2: Duplicate key value [uint8_t] "
                                + "declared for identity constraint \"parameterTypeNameKey\" of element \"SpaceSystem\"."),
                arguments("two units", types("""
                                <FloatParameterType name="t"><UnitSet><Unit>V</Unit><Unit>A</Unit></UnitSet>
                                  <IntegerDataEncoding/></FloatParameterType>"""),
                        "UnitSet in FloatParameterType 't' has more than one Unit, which is not supported"),
                arguments("unit power", types("""
                                <FloatParameterType name="t"><UnitSet><Unit power="2">m</Unit></UnitSet>
                                  <IntegerDataEncoding/></FloatParameterType>"""),
                        "Unit in UnitSet in FloatParameterType 't' has attribute power, which is not supported"),
                arguments("sign and magnitude", types(
                                "<IntegerParameterType name=\"t\"><IntegerDataEncoding encoding=\"signMagnitude\"/>"
                                        + "</IntegerParameterType>"),
                        "IntegerDataEncoding in IntegerParameterType 't' uses encoding signMagnitude; only unsigned "
                                + "and twosComplement are supported"),
                arguments("65 bits", types(
                                "<IntegerParameterType name=\"t\"><IntegerDataEncoding sizeInBits=\"65\"/>"
                                        + "</IntegerParameterType>"),
                        "IntegerDataEncoding in IntegerParameterType 't' is 65 bits; at most 64 are supported"),
                arguments("little endian of 12 bits", types("<IntegerParameterType name=\"t\"><IntegerDataEncoding "
                                + "sizeInBits=\"12\" byteOrder=\"leastSignificantByteFirst\"/></IntegerParameterType>"),
                        "IntegerDataEncoding in IntegerParameterType 't' is little-endian but 12 bits; little-endian "
                                + "integers must be whole bytes"),
                arguments("arbitrary byte order", types("<IntegerParameterType name=\"t\"><IntegerDataEncoding "
                                + "sizeInBits=\"16\" byteOrder=\"1,0\"/></IntegerParameterType>"),
                        "IntegerDataEncoding in IntegerParameterType 't' has byteOrder=\"1,0\"; only "
                                + "mostSignificantByteFirst and leastSignificantByteFirst are supported"),
                arguments("entry at the container start", containers(entry("<LocationInContainerInBits "
                                + "referenceLocation=\"containerStart\"><FixedValue>8</FixedValue>"
                                + "</LocationInContainerInBits>")),
                        "LocationInContainerInBits in ParameterRefEntry in EntryList in SequenceContainer 'A' has "
                                + "referenceLocation=\"containerStart\"; only \"previousEntry\" is supported"),
                arguments("dynamic entry location", containers(entry("<LocationInContainerInBits><DynamicValue>"
                                + "<ParameterInstanceRef parameterRef=\"ID\"/></DynamicValue>"
                                + "</LocationInContainerInBits>")),
                        "Unsupported XTCE element DynamicValue in LocationInContainerInBits in ParameterRefEntry in "
                                + "EntryList in SequenceContainer 'A'"),
                arguments("overlapping entry", containers(entry("<LocationInContainerInBits><FixedValue>-8"
                                + "</FixedValue></LocationInContainerInBits>")),
                        "LocationInContainerInBits in ParameterRefEntry in EntryList in SequenceContainer 'A' is -8 "
                                + "bits; an entry can't overlap the one before it"),
                arguments("repeated entry", containers(entry("<RepeatEntry><Count><FixedValue>2</FixedValue>"
                                + "</Count></RepeatEntry>")),
                        "Unsupported XTCE element RepeatEntry in ParameterRefEntry in EntryList in SequenceContainer "
                                + "'A'"),
                arguments("calibrated integer", types("""
                                <IntegerParameterType name="t"><IntegerDataEncoding><DefaultCalibrator>
                                  <PolynomialCalibrator><Term exponent="1" coefficient="2"/></PolynomialCalibrator>
                                </DefaultCalibrator></IntegerDataEncoding></IntegerParameterType>"""),
                        "Unsupported XTCE element DefaultCalibrator in IntegerDataEncoding in IntegerParameterType "
                                + "'t'"),
                arguments("context calibrator", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding><ContextCalibratorList>
                                  <ContextCalibrator>
                                    <ContextMatch><Comparison parameterRef="ID" value="1"/></ContextMatch>
                                    <Calibrator><PolynomialCalibrator><Term exponent="1" coefficient="2"/>
                                    </PolynomialCalibrator></Calibrator>
                                  </ContextCalibrator>
                                </ContextCalibratorList></IntegerDataEncoding></FloatParameterType>"""),
                        "Unsupported XTCE element ContextCalibratorList in IntegerDataEncoding in FloatParameterType "
                                + "'t'"),
                arguments("spline calibrator", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding><DefaultCalibrator>
                                  <SplineCalibrator><SplinePoint raw="0" calibrated="0"/>
                                    <SplinePoint raw="1" calibrated="2"/></SplineCalibrator>
                                </DefaultCalibrator></IntegerDataEncoding></FloatParameterType>"""),
                        "Unsupported XTCE element SplineCalibrator in DefaultCalibrator in IntegerDataEncoding in "
                                + "FloatParameterType 't'"),
                arguments("enumeration alarm", types("""
                                <EnumeratedParameterType name="m"><IntegerDataEncoding/><EnumerationList>
                                  <Enumeration value="0" label="SAFE"/></EnumerationList>
                                  <DefaultAlarm><EnumerationAlarmList>
                                    <EnumerationAlarm alarmLevel="warning" enumerationLabel="SAFE"/>
                                  </EnumerationAlarmList></DefaultAlarm></EnumeratedParameterType>"""),
                        "Unsupported XTCE element DefaultAlarm in EnumeratedParameterType 'm'"),
                arguments("context alarm", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><ContextAlarmList><ContextAlarm>
                                  <ContextMatch><Comparison parameterRef="ID" value="1"/></ContextMatch>
                                </ContextAlarm></ContextAlarmList></FloatParameterType>"""),
                        "Unsupported XTCE element ContextAlarmList in FloatParameterType 't'"),
                arguments("persistence", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm minViolations="3"/>
                                </FloatParameterType>"""),
                        "DefaultAlarm in FloatParameterType 't' has minViolations=\"3\"; only \"1\" is supported"),
                arguments("alarm conditions", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm><AlarmConditions>
                                  <WarningAlarm><Comparison parameterRef="ID" value="1"/></WarningAlarm>
                                </AlarmConditions></DefaultAlarm>
                                </FloatParameterType>"""),
                        "Unsupported XTCE element AlarmConditions in DefaultAlarm in FloatParameterType 't'"),
                arguments("inside range form", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm>
                                  <StaticAlarmRanges rangeForm="inside"><WarningRange minInclusive="1"/>
                                  </StaticAlarmRanges></DefaultAlarm></FloatParameterType>"""),
                        "StaticAlarmRanges in DefaultAlarm in FloatParameterType 't' has rangeForm=\"inside\"; only "
                                + "\"outside\" is supported"),
                arguments("watch range", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm><StaticAlarmRanges>
                                  <WatchRange minInclusive="1"/></StaticAlarmRanges></DefaultAlarm>
                                </FloatParameterType>"""),
                        "Unsupported XTCE element WatchRange in StaticAlarmRanges in DefaultAlarm in "
                                + "FloatParameterType 't'"),
                arguments("inclusive and exclusive", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm><StaticAlarmRanges>
                                  <WarningRange minInclusive="1" minExclusive="1"/></StaticAlarmRanges></DefaultAlarm>
                                </FloatParameterType>"""),
                        "WarningRange in StaticAlarmRanges in DefaultAlarm in FloatParameterType 't' has both "
                                + "minInclusive and minExclusive"),
                arguments("empty range", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm><StaticAlarmRanges>
                                  <CriticalRange minInclusive="2" maxInclusive="1"/></StaticAlarmRanges></DefaultAlarm>
                                </FloatParameterType>"""),
                        "CriticalRange in StaticAlarmRanges in DefaultAlarm in FloatParameterType 't' is empty: "
                                + "minimum 2.0 is above maximum 1.0"),
                arguments("ranges not nested", types("""
                                <FloatParameterType name="t"><IntegerDataEncoding/><DefaultAlarm><StaticAlarmRanges>
                                  <WarningRange minInclusive="7.0"/><CriticalRange minInclusive="7.12"/>
                                </StaticAlarmRanges></DefaultAlarm></FloatParameterType>"""),
                        "StaticAlarmRanges in DefaultAlarm in FloatParameterType 't' is inconsistent: the warning "
                                + "minimum 7.0 lies outside the critical minimum 7.12"),
                arguments("duplicate enumeration value", types("""
                                <EnumeratedParameterType name="m"><IntegerDataEncoding/><EnumerationList>
                                  <Enumeration value="0" label="A"/><Enumeration value="0" label="B"/>
                                </EnumerationList></EnumeratedParameterType>"""),
                        "EnumerationList in EnumeratedParameterType 'm' has value 0 more than once"),
                arguments("enumeration range", types("""
                                <EnumeratedParameterType name="m"><IntegerDataEncoding/><EnumerationList>
                                  <Enumeration value="0" maxValue="3" label="LOW"/>
                                </EnumerationList></EnumeratedParameterType>"""),
                        "Enumeration in EnumerationList in EnumeratedParameterType 'm' has attribute maxValue, "
                                + "which is not supported"),
                arguments("parameter reference", parameters("<ParameterRef parameterRef=\"ID\"/>"),
                        "Unsupported XTCE element ParameterRef in ParameterSet in TelemetryMetaData in SpaceSystem "
                                + "'TEST'"),
                arguments("parameter properties", parameters("""
                                <Parameter name="P" parameterTypeRef="uint8_t">
                                  <ParameterProperties dataSource="telemetered"/></Parameter>"""),
                        "Unsupported XTCE element ParameterProperties in Parameter 'P'"),
                arguments("unknown parameter type", parameters("<Parameter name=\"P\" parameterTypeRef=\"no_t\"/>"),
                        "Parameter 'P' refers to unknown parameter type no_t"),
                arguments("rate in stream", containers("""
                                <SequenceContainer name="R"><DefaultRateInStream minimumValue="1"/><EntryList/>
                                </SequenceContainer>"""),
                        "Unsupported XTCE element DefaultRateInStream in SequenceContainer 'R'"),
                arguments("container entry", containers("""
                                <SequenceContainer name="R"><EntryList><ContainerRefEntry containerRef="ROOT"/>
                                </EntryList></SequenceContainer>"""),
                        "Unsupported XTCE element ContainerRefEntry in EntryList in SequenceContainer 'R'"),
                arguments("include condition", containers("""
                                <SequenceContainer name="R"><EntryList><ParameterRefEntry parameterRef="ID">
                                  <IncludeCondition><Comparison parameterRef="ID" value="1"/></IncludeCondition>
                                </ParameterRefEntry></EntryList></SequenceContainer>"""),
                        "Unsupported XTCE element IncludeCondition in ParameterRefEntry in EntryList in "
                                + "SequenceContainer 'R'"),
                arguments("unknown entry", containers("""
                                <SequenceContainer name="R"><EntryList><ParameterRefEntry parameterRef="NOPE"/>
                                </EntryList></SequenceContainer>"""),
                        "ParameterRefEntry in EntryList in SequenceContainer 'R' refers to unknown parameter NOPE"),
                arguments("boolean expression", child("""
                                <BooleanExpression><Condition><ParameterInstanceRef parameterRef="ID"/>
                                  <ComparisonOperator>==</ComparisonOperator><Value>1</Value></Condition>
                                </BooleanExpression>"""),
                        "Unsupported XTCE element BooleanExpression in RestrictionCriteria in BaseContainer in "
                                + "SequenceContainer 'A'"),
                arguments("not equal", child("<Comparison parameterRef=\"ID\" value=\"1\" comparisonOperator=\"!=\"/>"),
                        "Comparison in RestrictionCriteria in BaseContainer in SequenceContainer 'A' has "
                                + "comparisonOperator=\"!=\"; only \"==\" is supported"),
                arguments("unknown compared parameter", child("<Comparison parameterRef=\"NOPE\" value=\"1\"/>"),
                        "Comparison in RestrictionCriteria in BaseContainer in SequenceContainer 'A' refers to "
                                + "unknown parameter NOPE"),
                arguments("two roots", containers(ROOT.replace("ROOT", "OTHER")),
                        "A mission database needs exactly one root container (one without a BaseContainer); found "
                                + "[ROOT, OTHER]"),
                arguments("no containers", Xtce.spaceSystem("<TelemetryMetaData><ParameterTypeSet>" + UINT8
                                + "</ParameterTypeSet><ParameterSet>" + ID + "</ParameterSet></TelemetryMetaData>"),
                        "A mission database needs exactly one root container (one without a BaseContainer); found "
                                + "[]"),
                arguments("unknown base", containers("""
                                <SequenceContainer name="A"><EntryList/><BaseContainer containerRef="NOPE"/>
                                </SequenceContainer>"""),
                        "SequenceContainer 'A' has unknown base container NOPE"),
                arguments("inheritance cycle", containers("""
                                <SequenceContainer name="A"><EntryList/><BaseContainer containerRef="B"/>
                                </SequenceContainer>
                                <SequenceContainer name="B"><EntryList/><BaseContainer containerRef="A"/>
                                </SequenceContainer>"""),
                        "Containers in an inheritance cycle: [A, B]"),
                arguments("restriction on own entry", containers("""
                                <SequenceContainer name="A"><EntryList><ParameterRefEntry parameterRef="X"/></EntryList>
                                  <BaseContainer containerRef="ROOT"><RestrictionCriteria>
                                    <Comparison parameterRef="X" value="1"/>
                                  </RestrictionCriteria></BaseContainer>
                                </SequenceContainer>""").replace(ID, ID + "<Parameter name=\"X\" "
                                + "parameterTypeRef=\"uint8_t\"/>"),
                        "The restriction of SequenceContainer 'A' compares X, which is not decoded before it"),
                arguments("integer literal", child("<Comparison parameterRef=\"ID\" value=\"one\"/>"),
                        "The restriction of SequenceContainer 'A' compares ID with 'one', which is not a valid "
                                + "value: For input string: \"one\""),
                arguments("unknown label", document(UINT8 + """
                                <EnumeratedParameterType name="mode_t"><IntegerDataEncoding/><EnumerationList>
                                  <Enumeration value="1" label="NOMINAL"/></EnumerationList></EnumeratedParameterType>""",
                                ID + "<Parameter name=\"MODE\" parameterTypeRef=\"mode_t\"/>",
                                ROOT.replace("<ParameterRefEntry parameterRef=\"ID\"/>",
                                        "<ParameterRefEntry parameterRef=\"MODE\"/>")
                                        + CHILD_OF_ROOT.formatted("<Comparison parameterRef=\"MODE\" value=\"BOGUS\"/>")),
                        "The restriction of SequenceContainer 'A' compares MODE with 'BOGUS', which is not a valid "
                                + "value: not a label of mode_t"),
                arguments("raw literal", child(
                                "<Comparison parameterRef=\"ID\" value=\"1.5\" useCalibratedValue=\"false\"/>"),
                        "The restriction of SequenceContainer 'A' compares ID with '1.5', which is not a valid "
                                + "value: For input string: \"1.5\""));
    }

    @Test
    void malformedXmlIsRejected() {
        assertThatThrownBy(() -> Xtce.read("<SpaceSystem")).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessageStartingWith("Not a valid XTCE 1.2 document: ");
    }

    @Test
    void documentTypeDeclarationsAreRefusedSoNoExternalEntityIsEverLoaded() {
        final var withEntity = "<!DOCTYPE SpaceSystem [<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>"
                + Xtce.spaceSystem("<LongDescription>&secret;</LongDescription>");

        assertThatThrownBy(() -> Xtce.read(withEntity)).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void documentThatBreaksTheXtceSchemaIsRejected() {
        assertThatThrownBy(() -> Xtce.read(Xtce.spaceSystem("<TelemetryMetaData><Bogus/></TelemetryMetaData>")))
                .isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessageStartingWith("Not a valid XTCE 1.2 document: cvc-complex-type");
    }

    @Test
    void unreadableStreamIsRejected() {
        final var broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };

        assertThatThrownBy(() -> XtceReader.read(broken)).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessage("Not a valid XTCE 1.2 document: disk gone");
    }

    @Test
    void stringsTimesLittleEndianIntegersAndEntryOffsetsAreRead() {
        final var database = Xtce.read(document(UINT8 + string("US-ASCII", "<SizeInBits><Fixed><FixedValue>32"
                        + "</FixedValue></Fixed></SizeInBits>")
                        + time("scale=\"0.001\"", "<IntegerDataEncoding sizeInBits=\"64\" "
                        + "byteOrder=\"leastSignificantByteFirst\"/>", "UNIX")
                        + "<IntegerParameterType name=\"le_t\" signed=\"true\"><IntegerDataEncoding "
                        + "sizeInBits=\"16\" encoding=\"twosComplement\" byteOrder=\"leastSignificantByteFirst\"/>"
                        + "</IntegerParameterType>",
                ID + "<Parameter name=\"NAME\" parameterTypeRef=\"s_t\"/><Parameter name=\"TIME\" "
                        + "parameterTypeRef=\"t_t\"/><Parameter name=\"LE\" parameterTypeRef=\"le_t\"/>",
                ROOT.replace("<ParameterRefEntry parameterRef=\"ID\"/>", "<ParameterRefEntry parameterRef=\"ID\"/>"
                        + "<ParameterRefEntry parameterRef=\"NAME\"/><ParameterRefEntry parameterRef=\"TIME\"/>"
                        + "<ParameterRefEntry parameterRef=\"LE\"><LocationInContainerInBits><FixedValue>8"
                        + "</FixedValue></LocationInContainerInBits></ParameterRefEntry>")));

        final var decoded = database.decode(Xtce.bytes("21 43545331 B2986DBBA1010000 FF FEFF"));

        assertThat(decoded.values()).extracting(ParameterValue::engineering).containsExactly(
                Optional.of(new IntegerValue(0x21)), Optional.of(new StringValue("CTS1")),
                Optional.of(new TimeValue(Instant.parse("2026-11-08T13:51:24.338Z"))),
                Optional.of(new IntegerValue(-2)));
        assertThat(database.root().entries().get(3).offsetInBits()).isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-08, 2026-11-08T00:00:00Z", "2026-11-08T13:51:24Z, 2026-11-08T13:51:24Z",
            "2026-11-08T13:51:24, 2026-11-08T13:51:24Z", "2026-11-08T15:51:24+02:00, 2026-11-08T13:51:24Z",
            "UNIX, 1970-01-01T00:00:00Z"})
    void epochIsADateADateAndTimeOrUnix(String epoch, Instant start) {
        final var database = Xtce.read(document(UINT8 + time("", "<IntegerDataEncoding/>", epoch),
                ID + "<Parameter name=\"TIME\" parameterTypeRef=\"t_t\"/>",
                ROOT.replace("<ParameterRefEntry parameterRef=\"ID\"/>", "<ParameterRefEntry parameterRef=\"ID\"/>"
                        + "<ParameterRefEntry parameterRef=\"TIME\"/>")));

        assertThat(database.decode(new byte[] {0, 10}).generationTime()).hasValue(start.plusSeconds(10));
    }

    private static String string(String encoding, String size) {
        return "<StringParameterType name=\"s_t\"><StringDataEncoding encoding=\"" + encoding + "\">" + size
                + "</StringDataEncoding></StringParameterType>";
    }

    private static String time(String encodingAttributes, String dataEncoding, String epoch) {
        return "<AbsoluteTimeParameterType name=\"t_t\"><Encoding " + encodingAttributes + ">" + dataEncoding
                + "</Encoding><ReferenceTime><Epoch>" + epoch + "</Epoch></ReferenceTime></AbsoluteTimeParameterType>";
    }

    private static String entry(String location) {
        return "<SequenceContainer name=\"A\"><EntryList><ParameterRefEntry parameterRef=\"ID\">" + location
                + "</ParameterRefEntry></EntryList><BaseContainer containerRef=\"ROOT\"/></SequenceContainer>";
    }

    private static Arguments arguments(String problem, String xtce, String message) {
        return Arguments.of(problem, xtce, message);
    }

    private static String types(String extra) {
        return document(UINT8 + extra, ID, ROOT);
    }

    private static String parameters(String extra) {
        return document(UINT8, ID + extra, ROOT);
    }

    private static String containers(String extra) {
        return document(UINT8, ID, ROOT + extra);
    }

    private static String child(String restriction) {
        return containers(CHILD_OF_ROOT.formatted(restriction));
    }

    private static String document(String types, String parameters, String containers) {
        return Xtce.spaceSystem("<TelemetryMetaData><ParameterTypeSet>" + types + "</ParameterTypeSet><ParameterSet>"
                + parameters + "</ParameterSet><ContainerSet>" + containers + "</ContainerSet></TelemetryMetaData>");
    }
}
