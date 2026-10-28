-- Additive upgrade: existing rides and payments are preserved.
ALTER TABLE rides ADD COLUMN ride_rating SMALLINT CHECK (ride_rating BETWEEN 1 AND 5);
ALTER TABLE rides ADD COLUMN scooter_rating SMALLINT CHECK (scooter_rating BETWEEN 1 AND 5);
ALTER TABLE rides ADD COLUMN ride_comment VARCHAR(1000);
ALTER TABLE rides ADD COLUMN scooter_comment VARCHAR(1000);
ALTER TABLE rides ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE rides ADD CONSTRAINT ride_review_complete CHECK (
    (reviewed_at IS NULL AND ride_rating IS NULL AND scooter_rating IS NULL AND ride_comment IS NULL AND scooter_comment IS NULL)
    OR (reviewed_at IS NOT NULL AND ride_rating IS NOT NULL AND scooter_rating IS NOT NULL AND status IN ('FINISHED', 'PAID'))
);
