CREATE TABLE transmitter (
    uuid             VARCHAR(30)      PRIMARY KEY,
    norad_id         INTEGER          NOT NULL REFERENCES satellite ON DELETE CASCADE,
    description      TEXT             NOT NULL,
    type             VARCHAR(20)      NOT NULL,
    status           VARCHAR(20)      NOT NULL,
    uplink_low_hz    BIGINT,
    uplink_high_hz   BIGINT,
    downlink_low_hz  BIGINT,
    downlink_high_hz BIGINT,
    mode             VARCHAR(50),
    uplink_mode      VARCHAR(50),
    baud             DOUBLE PRECISION,
    invert           BOOLEAN          NOT NULL,
    updated_at       TIMESTAMPTZ      NOT NULL,
    synced_at        TIMESTAMPTZ      NOT NULL
);

CREATE INDEX transmitter_satellite ON transmitter (norad_id);
