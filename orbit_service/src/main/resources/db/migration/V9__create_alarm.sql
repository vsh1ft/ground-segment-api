CREATE TABLE alarm (
    id              BIGSERIAL        PRIMARY KEY,
    version         BIGINT           NOT NULL,
    norad_id        INTEGER          NOT NULL,
    parameter       TEXT             NOT NULL,
    level           VARCHAR(10)      NOT NULL,
    state           VARCHAR(12)      NOT NULL,
    triggered_at    TIMESTAMPTZ      NOT NULL,
    trigger_value   DOUBLE PRECISION NOT NULL,
    returned_at     TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    cleared_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX alarm_open_per_parameter ON alarm (norad_id, parameter) WHERE state <> 'CLEARED';
