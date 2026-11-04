CREATE TABLE satellite (
    norad_id                 INTEGER      PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,
    international_designator VARCHAR(20)  NOT NULL,
    added_at                 TIMESTAMPTZ  NOT NULL
);

INSERT INTO satellite (norad_id, name, international_designator, added_at)
SELECT DISTINCT ON (norad_id)
       norad_id, object_name, object_id, MIN(downloaded_at) OVER (PARTITION BY norad_id)
FROM omm
ORDER BY norad_id, downloaded_at DESC;

ALTER TABLE omm
    ADD CONSTRAINT omm_satellite_fk FOREIGN KEY (norad_id) REFERENCES satellite ON DELETE CASCADE;

CREATE VIEW satellite_summary AS
SELECT s.norad_id, s.name, s.international_designator, s.added_at,
       (SELECT MAX(o.epoch) AT TIME ZONE 'UTC' FROM omm o WHERE o.norad_id = s.norad_id) AS latest_epoch
FROM satellite s;
