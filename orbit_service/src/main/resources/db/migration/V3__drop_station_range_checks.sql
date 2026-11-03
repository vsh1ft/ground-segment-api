ALTER TABLE station
    DROP CONSTRAINT station_latitude_deg_check,
    DROP CONSTRAINT station_longitude_deg_check,
    DROP CONSTRAINT station_min_elevation_deg_check;
