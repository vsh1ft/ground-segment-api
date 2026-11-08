CREATE TABLE tm_packet (
    id              BIGSERIAL   PRIMARY KEY,
    norad_id        INTEGER     NOT NULL,
    apid            INTEGER     NOT NULL,
    sequence_count  INTEGER     NOT NULL,
    service_type    INTEGER     NOT NULL,
    message_subtype INTEGER     NOT NULL,
    generation_time TIMESTAMPTZ NOT NULL,
    reception_time  TIMESTAMPTZ NOT NULL,
    raw             BYTEA       NOT NULL
);

CREATE INDEX tm_packet_satellite ON tm_packet (norad_id, id);
