CREATE TABLE event (
    id              BIGSERIAL   PRIMARY KEY,
    norad_id        INTEGER     NOT NULL,
    packet_id       BIGINT      NOT NULL REFERENCES tm_packet ON DELETE CASCADE,
    severity        VARCHAR(10) NOT NULL,
    event_id        INTEGER,
    name            TEXT,
    description     TEXT,
    container       TEXT,
    generation_time TIMESTAMPTZ NOT NULL
);

CREATE INDEX event_satellite ON event (norad_id, id);
