package lt.vsh1ft.simulator;

import lt.vsh1ft.ccsds.MessageType;

enum EventDefinition {

    BATTERY_LOW(1, MessageType.LOW_SEVERITY_ANOMALY_REPORT, true),
    BATTERY_RECOVERED(2, MessageType.INFORMATIVE_EVENT_REPORT, true),
    TEMPERATURE_HIGH(3, MessageType.MEDIUM_SEVERITY_ANOMALY_REPORT, true),
    TEMPERATURE_NORMAL(4, MessageType.INFORMATIVE_EVENT_REPORT, true),
    SAFE_MODE_ENTERED(5, MessageType.HIGH_SEVERITY_ANOMALY_REPORT, false),
    NOMINAL_MODE_RESUMED(6, MessageType.INFORMATIVE_EVENT_REPORT, false);

    private final int id;
    private final MessageType report;
    private final boolean hasAuxiliaryValue;

    EventDefinition(int id, MessageType report, boolean hasAuxiliaryValue) {
        this.id = id;
        this.report = report;
        this.hasAuxiliaryValue = hasAuxiliaryValue;
    }

    int id() {
        return id;
    }

    MessageType report() {
        return report;
    }

    boolean hasAuxiliaryValue() {
        return hasAuxiliaryValue;
    }
}
