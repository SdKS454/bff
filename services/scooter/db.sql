CREATE TABLE scooter (
 numeric_plate VARCHAR(64) PRIMARY KEY, charge INTEGER NOT NULL CHECK(charge BETWEEN 0 AND 100),
 status VARCHAR(20) NOT NULL CHECK(status IN ('READY','IN_RIDE','CHARGING','REPAIR')),
 position_x DOUBLE PRECISION NOT NULL, position_y DOUBLE PRECISION NOT NULL,
 is_deleted BOOLEAN NOT NULL DEFAULT false, reservation_id UUID, reserved_by UUID
);
CREATE INDEX scooter_ready ON scooter(status) WHERE NOT is_deleted;
-- Coordinates are sample grid values; no geographic distance or battery drain is inferred.
INSERT INTO scooter(numeric_plate,charge,status,position_x,position_y) VALUES
('A111AA77',95,'READY',10,15),('B222BB77',80,'READY',12,14),
('C333CC77',45,'READY',15,20),('D444DD77',100,'READY',8,10),
('G777GG77',5,'CHARGING',2,2),('H888HH77',0,'CHARGING',0,0);

-- Durable operation tombstones prevent a delayed reserve from reviving a cancelled ride.
CREATE TABLE scooter_reservations (
 ride_id UUID PRIMARY KEY, number_plate VARCHAR(64) NOT NULL,
 user_id UUID, released BOOLEAN NOT NULL
);
