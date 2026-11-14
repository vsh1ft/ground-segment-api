package lt.vsh1ft.groundstation.tmtc;

public record TelemetrySource(Long packetId, Long frameId) {

    public static TelemetrySource packet(long packetId) {
        return new TelemetrySource(packetId, null);
    }

    public static TelemetrySource frame(long frameId) {
        return new TelemetrySource(null, frameId);
    }

    @Override
    public String toString() {
        return packetId == null ? "frame " + frameId : "packet " + packetId;
    }
}
