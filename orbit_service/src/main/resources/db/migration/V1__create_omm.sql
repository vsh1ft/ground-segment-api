CREATE TABLE omm (
    id                  BIGSERIAL        PRIMARY KEY,
    norad_id            INTEGER          NOT NULL,
    object_name         VARCHAR(100)     NOT NULL,
    object_id           VARCHAR(20)      NOT NULL,
    epoch               TIMESTAMP        NOT NULL,
    mean_motion         DOUBLE PRECISION NOT NULL,
    eccentricity        DOUBLE PRECISION NOT NULL,
    inclination         DOUBLE PRECISION NOT NULL,
    ra_of_asc_node      DOUBLE PRECISION NOT NULL,
    arg_of_pericenter   DOUBLE PRECISION NOT NULL,
    mean_anomaly        DOUBLE PRECISION NOT NULL,
    ephemeris_type      INTEGER          NOT NULL,
    classification_type CHAR(1)          NOT NULL,
    element_set_no      INTEGER          NOT NULL,
    rev_at_epoch        INTEGER          NOT NULL,
    bstar               DOUBLE PRECISION NOT NULL,
    mean_motion_dot     DOUBLE PRECISION NOT NULL,
    mean_motion_ddot    DOUBLE PRECISION NOT NULL,
    downloaded_at       TIMESTAMPTZ      NOT NULL,
    UNIQUE (norad_id, epoch)
);

CREATE INDEX omm_latest_download ON omm (norad_id, downloaded_at DESC);
