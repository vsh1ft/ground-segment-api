package lt.vsh1ft.groundstation.elements;

import java.util.regex.Pattern;

record InternationalDesignator(int launchYear, int launchNumber, String piece) {

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-(\\d{3})([A-Z]{1,3})");

    static InternationalDesignator parse(String objectId) {
        final var matcher = FORMAT.matcher(objectId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not an international designator: " + objectId);
        }
        return new InternationalDesignator(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3));
    }
}
