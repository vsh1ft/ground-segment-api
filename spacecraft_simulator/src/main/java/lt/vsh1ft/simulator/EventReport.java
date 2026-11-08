package lt.vsh1ft.simulator;

import java.nio.ByteBuffer;

record EventReport(EventDefinition definition, int auxiliaryValue) {

    private static final int ID_LENGTH = 2;
    private static final int AUXILIARY_LENGTH = 2;

    static EventReport of(EventDefinition definition) {
        return new EventReport(definition, 0);
    }

    static EventReport of(EventDefinition definition, int auxiliaryValue) {
        return new EventReport(definition, auxiliaryValue);
    }

    byte[] toUserData() {
        final var buffer = ByteBuffer.allocate(ID_LENGTH + (definition.hasAuxiliaryValue() ? AUXILIARY_LENGTH : 0))
                .putShort((short) definition.id());
        if (definition.hasAuxiliaryValue()) {
            buffer.putShort((short) auxiliaryValue);
        }
        return buffer.array();
    }
}
