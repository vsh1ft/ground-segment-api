CREATE TABLE station (
    id                UUID             PRIMARY KEY,
    name              VARCHAR(100)     NOT NULL UNIQUE,
    latitude_deg      DOUBLE PRECISION NOT NULL CHECK (latitude_deg BETWEEN -90 AND 90),
    longitude_deg     DOUBLE PRECISION NOT NULL CHECK (longitude_deg BETWEEN -180 AND 180),
    altitude_m        DOUBLE PRECISION NOT NULL,
    min_elevation_deg DOUBLE PRECISION NOT NULL CHECK (min_elevation_deg BETWEEN -5 AND 90)
);
