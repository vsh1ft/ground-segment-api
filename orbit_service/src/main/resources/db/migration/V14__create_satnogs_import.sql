CREATE TABLE satnogs_import (
    norad_id       INTEGER     PRIMARY KEY,
    imported_until TIMESTAMPTZ NOT NULL
);
