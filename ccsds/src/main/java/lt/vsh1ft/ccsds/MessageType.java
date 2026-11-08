package lt.vsh1ft.ccsds;

public record MessageType(int serviceType, int messageSubtype) {

    public static final MessageType HOUSEKEEPING_PARAMETER_REPORT = new MessageType(3, 25);
    public static final MessageType INFORMATIVE_EVENT_REPORT = new MessageType(5, 1);
    public static final MessageType LOW_SEVERITY_ANOMALY_REPORT = new MessageType(5, 2);
    public static final MessageType MEDIUM_SEVERITY_ANOMALY_REPORT = new MessageType(5, 3);
    public static final MessageType HIGH_SEVERITY_ANOMALY_REPORT = new MessageType(5, 4);
    private static final int MAX_ID = 0xFF;

    public MessageType {
        Fields.requireRange("service type", serviceType, 0, MAX_ID);
        Fields.requireRange("message subtype", messageSubtype, 0, MAX_ID);
    }
}
