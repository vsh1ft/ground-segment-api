package lt.vsh1ft.groundstation.station;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "ID of a newly created resource")
public record IdResponse(
        @Schema(description = "Generated ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        UUID id) {
}
