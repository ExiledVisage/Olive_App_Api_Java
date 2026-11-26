-- work_schedules: haftalik calisma plani (docs/ATTENDANCE.md). user_id bos satirlar fabrika geneli; kisiye ait
-- satir varsa o kisinin plani yalniz onlardan olusur (satiri olmayan gun izin). Saatler yerel saat.

CREATE TABLE work_schedules (
    id bigint NOT NULL,
    user_id bigint,
    weekday smallint NOT NULL,
    starts_at time(0) without time zone NOT NULL,
    ends_at time(0) without time zone NOT NULL,
    break_minutes integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT work_schedules_weekday_check CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT work_schedules_range_check CHECK (ends_at > starts_at),
    CONSTRAINT work_schedules_break_check CHECK (break_minutes >= 0)
);

CREATE SEQUENCE work_schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE work_schedules_id_seq OWNED BY work_schedules.id;

ALTER TABLE ONLY work_schedules ALTER COLUMN id SET DEFAULT nextval('work_schedules_id_seq'::regclass);

ALTER TABLE ONLY work_schedules
    ADD CONSTRAINT work_schedules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY work_schedules
    ADD CONSTRAINT work_schedules_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX work_schedules_user_day_unique ON work_schedules USING btree (user_id, weekday)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX work_schedules_default_day_unique ON work_schedules USING btree (weekday)
    WHERE user_id IS NULL;

-- Fabrika geneli varsayilan: Pazartesi-Cuma 10:00-18:00, gunde 8 saat

insert into work_schedules (weekday, starts_at, ends_at, break_minutes) values
	(1, '10:00', '18:00', 0),
	(2, '10:00', '18:00', 0),
	(3, '10:00', '18:00', 0),
	(4, '10:00', '18:00', 0),
	(5, '10:00', '18:00', 0);
