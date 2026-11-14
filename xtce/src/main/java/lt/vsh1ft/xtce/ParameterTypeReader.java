package lt.vsh1ft.xtce;

import static lt.vsh1ft.xtce.XtceElements.NAME;
import static lt.vsh1ft.xtce.XtceElements.SHORT_DESCRIPTION;
import static lt.vsh1ft.xtce.XtceElements.attribute;
import static lt.vsh1ft.xtce.XtceElements.attributes;
import static lt.vsh1ft.xtce.XtceElements.invalid;
import static lt.vsh1ft.xtce.XtceElements.requireDefault;
import static lt.vsh1ft.xtce.XtceElements.unsupported;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Element;

final class ParameterTypeReader {

    private static final String INTEGER_DATA_ENCODING = "IntegerDataEncoding";
    private static final String UNIT_SET = "UnitSet";

    private ParameterTypeReader() {
    }

    static ParameterType read(Element type) {
        return switch (type.getLocalName()) {
            case "IntegerParameterType" -> readInteger(type);
            case "FloatParameterType" -> readFloat(type);
            case "EnumeratedParameterType" -> readEnumerated(type);
            case "StringParameterType" -> readString(type);
            case "AbsoluteTimeParameterType" -> readTime(type);
            default -> throw unsupported(type);
        };
    }

    private static ParameterType readInteger(Element type) {
        attributes(type, NAME, SHORT_DESCRIPTION, "signed", "sizeInBits");
        final var numeric = readNumeric(type, false);
        return new IntegerParameterType(type.getAttribute(NAME), numeric.unit(), numeric.encoding().encoding(),
                numeric.limits());
    }

    private static ParameterType readFloat(Element type) {
        attributes(type, NAME, SHORT_DESCRIPTION, "sizeInBits");
        final var numeric = readNumeric(type, true);
        return new FloatParameterType(type.getAttribute(NAME), numeric.unit(), numeric.encoding().encoding(),
                numeric.encoding().calibrator(), numeric.limits());
    }

    private static Numeric readNumeric(Element type, boolean calibrated) {
        String unit = null;
        Encoding encoding = null;
        var limits = AlarmRanges.NONE;
        for (var child : Xml.children(type)) {
            switch (child.getLocalName()) {
                case UNIT_SET -> unit = readUnit(child);
                case INTEGER_DATA_ENCODING -> encoding = readEncoding(child, calibrated);
                case "DefaultAlarm" -> limits = readAlarm(child);
                default -> throw unsupported(child);
            }
        }
        return new Numeric(unit, required(type, encoding), limits);
    }

    private static ParameterType readEnumerated(Element type) {
        attributes(type, NAME, SHORT_DESCRIPTION);
        Encoding encoding = null;
        var enumeration = new Enumeration(Map.of(), Map.of());
        for (var child : Xml.children(type)) {
            switch (child.getLocalName()) {
                case UNIT_SET -> readUnit(child);
                case INTEGER_DATA_ENCODING -> encoding = readEncoding(child, false);
                case "EnumerationList" -> enumeration = readEnumeration(child);
                default -> throw unsupported(child);
            }
        }
        return new EnumeratedParameterType(type.getAttribute(NAME), required(type, encoding).encoding(),
                enumeration.labels(), enumeration.descriptions());
    }

    private static Encoding required(Element type, Encoding encoding) {
        if (encoding == null) {
            throw invalid(type, "has no IntegerDataEncoding");
        }
        return encoding;
    }

    private static ParameterType readString(Element type) {
        attributes(type, NAME, SHORT_DESCRIPTION);
        final var children = Xml.children(type);
        if (children.size() != 1 || !children.getFirst().getLocalName().equals("StringDataEncoding")) {
            throw invalid(type, "needs exactly one StringDataEncoding and nothing else");
        }
        final var encoding = children.getFirst();
        attributes(encoding, "encoding", "byteOrder", "bitOrder");
        if (!encoding.getAttribute("encoding").equals("US-ASCII")) {
            throw invalid(encoding, "must have encoding=\"US-ASCII\"; other character sets are not supported");
        }
        requireDefault(encoding, "byteOrder", "mostSignificantByteFirst");
        requireDefault(encoding, "bitOrder", "mostSignificantBitFirst");
        final long sizeInBits = readStringSize(encoding);
        if (sizeInBits % Byte.SIZE != 0 || sizeInBits > IntegerEncoding.MAX_SIZE_IN_BITS) {
            throw invalid(encoding, "is " + sizeInBits + " bits; strings of whole bytes up to "
                    + IntegerEncoding.MAX_SIZE_IN_BITS + " bits are supported");
        }
        return new StringParameterType(type.getAttribute(NAME), new IntegerEncoding((int) sizeInBits, false));
    }

    private static long readStringSize(Element encoding) {
        final var size = Xml.children(encoding).getFirst();
        if (!size.getLocalName().equals("SizeInBits")) {
            throw unsupported(size);
        }
        final var parts = Xml.children(size);
        if (parts.size() != 1) {
            throw unsupported(parts.get(1));
        }
        return Long.parseLong(Xml.children(parts.getFirst()).getFirst().getTextContent().strip());
    }

    private static ParameterType readTime(Element type) {
        attributes(type, NAME, SHORT_DESCRIPTION);
        Element encoding = null;
        Instant epoch = null;
        for (var child : Xml.children(type)) {
            if (child.getLocalName().equals("Encoding")) {
                encoding = child;
            } else {
                epoch = readEpoch(child);
            }
        }
        if (encoding == null || epoch == null) {
            throw invalid(type, "needs an Encoding and a ReferenceTime with an Epoch");
        }
        attributes(encoding, "units", "scale", "offset");
        requireDefault(encoding, "units", "seconds");
        final var data = Xml.children(encoding).getFirst();
        if (!data.getLocalName().equals(INTEGER_DATA_ENCODING)) {
            throw unsupported(data);
        }
        return new AbsoluteTimeParameterType(type.getAttribute(NAME), readEncoding(data, false).encoding(), epoch,
                new BigDecimal(attribute(encoding, "scale", "1")), new BigDecimal(attribute(encoding, "offset", "0")));
    }

    private static Instant readEpoch(Element referenceTime) {
        final var epoch = Xml.children(referenceTime).getFirst();
        if (!epoch.getLocalName().equals("Epoch")) {
            throw unsupported(epoch);
        }
        final var value = epoch.getTextContent().strip();
        if (value.equals("UNIX")) {
            return Instant.EPOCH;
        }
        try {
            if (!value.contains("T")) {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            final var time = DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from,
                    LocalDateTime::from);
            return time instanceof OffsetDateTime zoned ? zoned.toInstant()
                    : ((LocalDateTime) time).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw invalid(epoch, "is " + value + "; only a date, a date and time or UNIX are supported");
        }
    }

    private static String readUnit(Element unitSet) {
        final var units = Xml.children(unitSet);
        if (units.size() > 1) {
            throw invalid(unitSet, "has more than one Unit, which is not supported");
        }
        units.forEach(unit -> attributes(unit));
        return units.isEmpty() ? null : units.getFirst().getTextContent().strip();
    }

    private static Encoding readEncoding(Element encoding, boolean calibrated) {
        attributes(encoding, "encoding", "sizeInBits", "byteOrder", "bitOrder");
        final boolean signed = switch (attribute(encoding, "encoding", "unsigned")) {
            case "unsigned" -> false;
            case "twosComplement" -> true;
            default -> throw invalid(encoding, "uses encoding " + encoding.getAttribute("encoding")
                    + "; only unsigned and twosComplement are supported");
        };
        final long sizeInBits = Long.parseLong(attribute(encoding, "sizeInBits", "8"));
        if (sizeInBits > IntegerEncoding.MAX_SIZE_IN_BITS) {
            throw invalid(encoding, "is " + sizeInBits + " bits; at most " + IntegerEncoding.MAX_SIZE_IN_BITS
                    + " are supported");
        }
        final boolean littleEndian = switch (attribute(encoding, "byteOrder", "mostSignificantByteFirst")) {
            case "mostSignificantByteFirst" -> false;
            case "leastSignificantByteFirst" -> true;
            default -> throw invalid(encoding, "has byteOrder=\"" + encoding.getAttribute("byteOrder")
                    + "\"; only mostSignificantByteFirst and leastSignificantByteFirst are supported");
        };
        if (littleEndian && sizeInBits % Byte.SIZE != 0) {
            throw invalid(encoding, "is little-endian but " + sizeInBits + " bits; little-endian integers must be "
                    + "whole bytes");
        }
        requireDefault(encoding, "bitOrder", "mostSignificantBitFirst");
        var calibrator = PolynomialCalibrator.IDENTITY;
        for (var child : Xml.children(encoding)) {
            if (!calibrated || !child.getLocalName().equals("DefaultCalibrator")) {
                throw unsupported(child);
            }
            calibrator = readCalibrator(child);
        }
        return new Encoding(new IntegerEncoding((int) sizeInBits, signed, littleEndian), calibrator);
    }

    private static PolynomialCalibrator readCalibrator(Element defaultCalibrator) {
        attributes(defaultCalibrator, NAME, SHORT_DESCRIPTION);
        final var calibrator = Xml.children(defaultCalibrator).getFirst();
        if (!calibrator.getLocalName().equals("PolynomialCalibrator")) {
            throw unsupported(calibrator);
        }
        attributes(calibrator, NAME, SHORT_DESCRIPTION);
        final var terms = new ArrayList<PolynomialCalibrator.Term>();
        for (var term : Xml.children(calibrator)) {
            attributes(term, "coefficient", "exponent");
            terms.add(new PolynomialCalibrator.Term(Integer.parseInt(term.getAttribute("exponent")),
                    Double.parseDouble(term.getAttribute("coefficient"))));
        }
        return new PolynomialCalibrator(terms);
    }

    private static AlarmRanges readAlarm(Element defaultAlarm) {
        attributes(defaultAlarm, NAME, SHORT_DESCRIPTION, "minViolations");
        requireDefault(defaultAlarm, "minViolations", "1");
        var limits = AlarmRanges.NONE;
        for (var child : Xml.children(defaultAlarm)) {
            if (!child.getLocalName().equals("StaticAlarmRanges")) {
                throw unsupported(child);
            }
            limits = readRanges(child);
        }
        return limits;
    }

    private static AlarmRanges readRanges(Element staticAlarmRanges) {
        attributes(staticAlarmRanges, NAME, SHORT_DESCRIPTION, "rangeForm");
        requireDefault(staticAlarmRanges, "rangeForm", "outside");
        var warning = AlarmRange.UNBOUNDED;
        var critical = AlarmRange.UNBOUNDED;
        for (var range : Xml.children(staticAlarmRanges)) {
            switch (range.getLocalName()) {
                case "WarningRange" -> warning = readRange(range);
                case "CriticalRange" -> critical = readRange(range);
                default -> throw unsupported(range);
            }
        }
        try {
            return new AlarmRanges(warning, critical);
        } catch (IllegalArgumentException e) {
            throw invalid(staticAlarmRanges, "is inconsistent: " + e.getMessage());
        }
    }

    private static AlarmRange readRange(Element range) {
        attributes(range, "minInclusive", "minExclusive", "maxInclusive", "maxExclusive");
        final var min = bound(range, "minInclusive", "minExclusive", Double.NEGATIVE_INFINITY);
        final var max = bound(range, "maxInclusive", "maxExclusive", Double.POSITIVE_INFINITY);
        try {
            return new AlarmRange(min.value(), min.inclusive(), max.value(), max.inclusive());
        } catch (IllegalArgumentException e) {
            throw invalid(range, "is empty: " + e.getMessage());
        }
    }

    private static Bound bound(Element range, String inclusive, String exclusive, double open) {
        if (range.hasAttribute(inclusive) && range.hasAttribute(exclusive)) {
            throw invalid(range, "has both " + inclusive + " and " + exclusive);
        }
        if (range.hasAttribute(exclusive)) {
            return new Bound(Double.parseDouble(range.getAttribute(exclusive)), false);
        }
        return new Bound(Double.parseDouble(attribute(range, inclusive, String.valueOf(open))), true);
    }

    private static Enumeration readEnumeration(Element enumerationList) {
        final var labels = new LinkedHashMap<Long, String>();
        final var descriptions = new LinkedHashMap<Long, String>();
        for (var enumeration : Xml.children(enumerationList)) {
            attributes(enumeration, "value", "label", SHORT_DESCRIPTION);
            final long value = Long.parseLong(enumeration.getAttribute("value"));
            if (labels.putIfAbsent(value, enumeration.getAttribute("label")) != null) {
                throw invalid(enumerationList, "has value " + value + " more than once");
            }
            if (enumeration.hasAttribute(SHORT_DESCRIPTION)) {
                descriptions.put(value, enumeration.getAttribute(SHORT_DESCRIPTION));
            }
        }
        return new Enumeration(labels, descriptions);
    }

    private record Numeric(String unit, Encoding encoding, AlarmRanges limits) {
    }

    private record Encoding(IntegerEncoding encoding, PolynomialCalibrator calibrator) {
    }

    private record Bound(double value, boolean inclusive) {
    }

    private record Enumeration(Map<Long, String> labels, Map<Long, String> descriptions) {
    }
}
