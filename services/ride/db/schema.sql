CREATE TABLE rates (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL,
 unlock_fee NUMERIC(14,2) NOT NULL CHECK(unlock_fee>=0),
 price_per_minute NUMERIC(14,2) NOT NULL CHECK(price_per_minute>=0),
 currency CHAR(3) NOT NULL, is_default BOOLEAN NOT NULL DEFAULT false,
 active BOOLEAN NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX rates_default ON rates(is_default) WHERE is_default AND active;
-- Agreed educational tariff. Existing rides retain their own tariff snapshot.
INSERT INTO rates VALUES('6ba7b810-9dad-11d1-80b4-00c04fd430c8','Учебный поминутный',50,8,'RUB',true,true);
CREATE TABLE rides (
 id UUID PRIMARY KEY,user_id UUID NOT NULL,number_plate VARCHAR(64) NOT NULL,rate_id UUID NOT NULL REFERENCES rates(id),
 status VARCHAR(16) NOT NULL CHECK(status IN('STARTING','CANCELLING','ACTIVE','FINISHING','FINISHED','PAID')),
 payment_mode VARCHAR(16) NOT NULL CHECK(payment_mode IN('EDUCATIONAL','DISABLED')),
 started_at TIMESTAMPTZ NOT NULL,finished_at TIMESTAMPTZ,
 start_pos_x DOUBLE PRECISION,start_pos_y DOUBLE PRECISION,end_pos_x DOUBLE PRECISION,end_pos_y DOUBLE PRECISION,
 duration_seconds BIGINT CHECK(duration_seconds>=0),amount NUMERIC(14,2) CHECK(amount>=0),currency CHAR(3) NOT NULL,paid_at TIMESTAMPTZ,
 unlock_fee NUMERIC(14,2) NOT NULL CHECK(unlock_fee>=0),price_per_minute NUMERIC(14,2) NOT NULL CHECK(price_per_minute>=0),
 CHECK((status IN('STARTING','CANCELLING','ACTIVE') AND finished_at IS NULL AND amount IS NULL AND paid_at IS NULL)
 OR(status IN('FINISHING','FINISHED') AND finished_at IS NOT NULL AND amount IS NOT NULL AND paid_at IS NULL)
 OR(status='PAID' AND finished_at IS NOT NULL AND amount IS NOT NULL AND paid_at IS NOT NULL))
);
CREATE UNIQUE INDEX rides_active_user ON rides(user_id) WHERE status IN('STARTING','CANCELLING','ACTIVE','FINISHING');
CREATE UNIQUE INDEX rides_active_scooter ON rides(number_plate) WHERE status IN('STARTING','CANCELLING','ACTIVE','FINISHING');
CREATE INDEX rides_user_history ON rides(user_id,started_at DESC);
