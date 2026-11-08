package lt.vsh1ft.simulator;

import java.nio.ByteBuffer;
import java.util.Objects;

record HousekeepingReport(int batteryVoltageMillivolts, int temperatureDeciCelsius, SpacecraftMode mode) {

    static final int STRUCTURE_ID = 1;
    static final int LENGTH = 6;

    HousekeepingReport {
        Objects.requireNonNull(mode, "mode");
        if (batteryVoltageMillivolts < 0 || batteryVoltageMillivolts > 0xFFFF) {
            throw new IllegalArgumentException("battery voltage must fit 16 bits unsigned, was "
                    + batteryVoltageMillivolts + " mV");
        }
        if (temperatureDeciCelsius < Short.MIN_VALUE || temperatureDeciCelsius > Short.MAX_VALUE) {
            throw new IllegalArgumentException("temperature must fit 16 bits signed, was "
                    + temperatureDeciCelsius + " x 0.1 degC");
        }
    }

    byte[] toUserData() {
        return ByteBuffer.allocate(LENGTH)
                .put((byte) STRUCTURE_ID)
                .putShort((short) batteryVoltageMillivolts)
                .putShort((short) temperatureDeciCelsius)
                .put((byte) mode.ordinal())
                .array();
    }
}
