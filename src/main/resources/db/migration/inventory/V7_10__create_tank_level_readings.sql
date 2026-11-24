-- tank_level_readings: cetvelle seviye olcumu (mm) ve tank geometrisinden hesaplanan miktar, defterle fark

CREATE TABLE tank_level_readings (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    measured_mm numeric(10,2) NOT NULL,
    calculated_litre numeric(14,2) NOT NULL,
    calculated_kg numeric(14,2),
    density_kg_per_litre numeric(8,4),
    book_quantity numeric(14,2) NOT NULL,
    unit character varying(20) NOT NULL,
    difference numeric(14,2),
    measured_by_user_id bigint,
    note text,
    measured_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT tank_level_readings_unit_check CHECK (unit IN ('KG', 'LITRE'))
);

CREATE SEQUENCE tank_level_readings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE tank_level_readings_id_seq OWNED BY tank_level_readings.id;

ALTER TABLE ONLY tank_level_readings ALTER COLUMN id SET DEFAULT nextval('tank_level_readings_id_seq'::regclass);

ALTER TABLE ONLY tank_level_readings
    ADD CONSTRAINT tank_level_readings_pkey PRIMARY KEY (id);

CREATE INDEX tank_level_readings_storage_unit_id_index ON tank_level_readings USING btree (storage_unit_id, measured_at);

ALTER TABLE ONLY tank_level_readings
    ADD CONSTRAINT tank_level_readings_measured_by_user_id_foreign FOREIGN KEY (measured_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY tank_level_readings
    ADD CONSTRAINT tank_level_readings_storage_unit_id_foreign FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE RESTRICT;
