package lt.vsh1ft.groundstation.satnogs;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lt.vsh1ft.groundstation.frame.Reception;

record SatnogsObservation(
        long id,
        Instant start,
        Instant end,
        @JsonProperty("station_name") String stationName,
        @JsonProperty("station_lat") Double stationLat,
        @JsonProperty("station_lng") Double stationLng,
        @JsonProperty("station_alt") Double stationAlt,
        List<Demoddata> demoddata) {

    SatnogsObservation {
        demoddata = demoddata == null ? List.of() : List.copyOf(demoddata);
    }

    Reception receivedAt(Instant time) {
        return Reception.satnogs(stationName, stationLat, stationLng, stationAlt, id, time);
    }

    record Demoddata(@JsonProperty("payload_demod") String payloadDemod) {
    }
}
