CREATE TABLE parameter_value (
    id                 BIGSERIAL        PRIMARY KEY,
    norad_id           INTEGER          NOT NULL,
    packet_id          BIGINT           NOT NULL REFERENCES tm_packet ON DELETE CASCADE,
    parameter          TEXT             NOT NULL,
    generation_time    TIMESTAMPTZ      NOT NULL,
    raw                BIGINT           NOT NULL,
    engineering_number DOUBLE PRECISION,
    engineering_text   TEXT
);

CREATE INDEX parameter_value_history ON parameter_value (norad_id, parameter, id);
