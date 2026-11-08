ALTER TABLE alarm ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'LIMIT';
ALTER TABLE alarm ALTER COLUMN kind DROP DEFAULT;
ALTER TABLE alarm ALTER COLUMN parameter DROP NOT NULL;
ALTER TABLE alarm ALTER COLUMN trigger_value DROP NOT NULL;
ALTER TABLE alarm ADD CONSTRAINT alarm_limit_has_parameter_and_value
    CHECK ((kind = 'LIMIT') = (parameter IS NOT NULL AND trigger_value IS NOT NULL));

DROP INDEX alarm_open_per_parameter;
CREATE UNIQUE INDEX alarm_open_per_kind_and_parameter ON alarm (norad_id, kind, parameter) NULLS NOT DISTINCT
    WHERE state <> 'CLEARED';
