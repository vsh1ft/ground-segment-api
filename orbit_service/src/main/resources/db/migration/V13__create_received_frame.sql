CREATE TABLE received_frame (
    id                BIGSERIAL   PRIMARY KEY,
    norad_id          INTEGER     NOT NULL,
    raw               BYTEA       NOT NULL,
    raw_sha256        BYTEA       NOT NULL GENERATED ALWAYS AS (sha256(raw)) STORED,
    first_received_at TIMESTAMPTZ NOT NULL,
    generation_time   TIMESTAMPTZ,
    container         TEXT,
    crc_valid         BOOLEAN,
    UNIQUE (norad_id, raw_sha256)
);

CREATE INDEX received_frame_satellite ON received_frame (norad_id, id);

CREATE TABLE frame_reception (
    id            BIGSERIAL        PRIMARY KEY,
    frame_id      BIGINT           NOT NULL REFERENCES received_frame ON DELETE CASCADE,
    source        VARCHAR(8)       NOT NULL,
    station_id    UUID,
    station       TEXT             NOT NULL,
    latitude_deg  DOUBLE PRECISION,
    longitude_deg DOUBLE PRECISION,
    altitude_m    DOUBLE PRECISION,
    observation   BIGINT,
    received_at   TIMESTAMPTZ      NOT NULL,
    UNIQUE NULLS NOT DISTINCT (frame_id, source, station_id, station, observation, received_at)
);

ALTER TABLE parameter_value ALTER COLUMN packet_id DROP NOT NULL;
ALTER TABLE parameter_value ADD COLUMN frame_id BIGINT REFERENCES received_frame ON DELETE CASCADE;
ALTER TABLE parameter_value ADD CONSTRAINT parameter_value_from_packet_or_frame
    CHECK ((packet_id IS NULL) <> (frame_id IS NULL));
