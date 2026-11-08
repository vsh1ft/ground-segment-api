package lt.vsh1ft.ccsds;

public record MessageType(int serviceType, int messageSubtype) {

    public static final MessageType HOUSEKEEPING_PARAMETER_REPORT = new MessageType(3, 25);
    private static final int MAX_ID = 0xFF;

    public MessageType {
        Fields.requireRange("service type", serviceType, 0, MAX_ID);
        Fields.requireRange("message subtype", messageSubtype, 0, MAX_ID);
    }
}
