package lt.vsh1ft.groundstation.elements;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.orekit.files.ccsds.definitions.Units;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.units.Unit;

final class OmmTleConverter {

    private OmmTleConverter() {
    }

    static TLE toTle(StoredOmm omm) {
        final var designator = InternationalDesignator.parse(omm.objectId());
        final var utc = TimeScalesFactory.getUTC();
        return new TLE(
                omm.noradId(),
                omm.classificationType().charAt(0),
                designator.launchYear(),
                designator.launchNumber(),
                designator.piece(),
                omm.ephemerisType(),
                omm.elementSetNo(),
                new AbsoluteDate(omm.epoch().toInstant(ZoneOffset.UTC), utc),
                Units.REV_PER_DAY.toSI(omm.meanMotion()),
                Units.REV_PER_DAY2_SCALED.toSI(omm.meanMotionDot()),
                Units.REV_PER_DAY3_SCALED.toSI(omm.meanMotionDdot()),
                omm.eccentricity(),
                Unit.DEGREE.toSI(omm.inclination()),
                Unit.DEGREE.toSI(omm.argOfPericenter()),
                Unit.DEGREE.toSI(omm.raOfAscNode()),
                Unit.DEGREE.toSI(omm.meanAnomaly()),
                omm.revAtEpoch(),
                Units.ONE_PER_ER.toSI(omm.bstar()),
                utc);
    }

    static StoredOmm toOmm(String name, TLE tle, Instant receivedAt) {
        return new StoredOmm(
                null,
                tle.getSatelliteNumber(),
                name,
                designator(tle),
                LocalDateTime.ofInstant(tle.getDate().toInstant(), ZoneOffset.UTC),
                Units.REV_PER_DAY.fromSI(tle.getMeanMotion()),
                tle.getE(),
                Unit.DEGREE.fromSI(tle.getI()),
                Unit.DEGREE.fromSI(tle.getRaan()),
                Unit.DEGREE.fromSI(tle.getPerigeeArgument()),
                Unit.DEGREE.fromSI(tle.getMeanAnomaly()),
                tle.getEphemerisType(),
                String.valueOf(tle.getClassification()),
                tle.getElementNumber(),
                tle.getRevolutionNumberAtEpoch(),
                Units.ONE_PER_ER.fromSI(tle.getBStar()),
                Units.REV_PER_DAY2_SCALED.fromSI(tle.getMeanMotionFirstDerivative()),
                Units.REV_PER_DAY3_SCALED.fromSI(tle.getMeanMotionSecondDerivative()),
                receivedAt);
    }

    private static String designator(TLE tle) {
        return "%04d-%03d%s".formatted(tle.getLaunchYear(), tle.getLaunchNumber(), tle.getLaunchPiece().strip());
    }
}
