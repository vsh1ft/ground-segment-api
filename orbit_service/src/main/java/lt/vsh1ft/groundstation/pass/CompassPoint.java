package lt.vsh1ft.groundstation.pass;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Direction on the 16-point compass, each point covering 22.5 degrees of azimuth")
public enum CompassPoint {
    N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW;

    private static final double DEGREES_PER_POINT = 22.5;

    static CompassPoint of(double azimuthDeg) {
        return values()[(int) Math.round(azimuthDeg / DEGREES_PER_POINT) % values().length];
    }
}
