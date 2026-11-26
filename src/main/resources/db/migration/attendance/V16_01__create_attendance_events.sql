-- attendance_events: fabrika giris-cikislari (docs/ATTENDANCE.md). QR okutma (scan) ya da yonetici kaydi (manual);
-- duzeltmede eski kayit silinmez, iptal edilir (voided_*) ve yerine yeni kayit girilir

CREATE TABLE attendance_events (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    kind character varying(3) NOT NULL,
    occurred_at timestamp(0) without time zone NOT NULL,
    source character varying(10) NOT NULL,
    recorded_by_user_id bigint,
    device_label character varying(100),
    note text,
    voided_at timestamp(0) without time zone,
    voided_by_user_id bigint,
    void_reason text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT attendance_events_kind_check CHECK (kind IN ('in', 'out')),
    CONSTRAINT attendance_events_source_check CHECK (source IN ('scan', 'manual'))
);

CREATE SEQUENCE attendance_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE attendance_events_id_seq OWNED BY attendance_events.id;

ALTER TABLE ONLY attendance_events ALTER COLUMN id SET DEFAULT nextval('attendance_events_id_seq'::regclass);

ALTER TABLE ONLY attendance_events
    ADD CONSTRAINT attendance_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY attendance_events
    ADD CONSTRAINT attendance_events_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY attendance_events
    ADD CONSTRAINT attendance_events_recorded_by_fk FOREIGN KEY (recorded_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY attendance_events
    ADD CONSTRAINT attendance_events_voided_by_fk FOREIGN KEY (voided_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX attendance_events_user_time_index ON attendance_events USING btree (user_id, occurred_at);
