package lt.vsh1ft.ccsds;

public record Apid(int value) {

    public static final int MAX = 0x7FF;

    public Apid {
        Fields.requireRange("APID", value, 0, MAX);
    }
}
