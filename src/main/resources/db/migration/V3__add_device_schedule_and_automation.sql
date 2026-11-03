ALTER TABLE devices
ADD COLUMN schedule_mode varchar(32) NOT NULL DEFAULT 'MANUAL_OFF',
ADD COLUMN auto_shutoff_enabled boolean NOT NULL DEFAULT false,
ADD COLUMN auto_shutoff_seconds integer NOT NULL DEFAULT 300,
ADD COLUMN weekly_schedule text;
