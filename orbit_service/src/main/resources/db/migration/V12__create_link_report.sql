CREATE TABLE link_report (
    id                  BIGSERIAL   PRIMARY KEY,
    norad_id            INTEGER     NOT NULL,
    pass_start          TIMESTAMPTZ NOT NULL,
    pass_end            TIMESTAMPTZ NOT NULL,
    frames_received     BIGINT      NOT NULL,
    frames_lost         BIGINT      NOT NULL,
    frames_failed_check BIGINT      NOT NULL,
    idle_frames         BIGINT      NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    UNIQUE (norad_id, pass_start)
);
