-- tank_measurements: son tank olcum gecmisi (hesaplanan, olculen, fark)

CREATE TABLE tank_measurements (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    material_id bigint NOT NULL,
    production_product_attribute_definition_id bigint NOT NULL,
    calculated_value numeric(14,4),
    measured_value numeric(14,4) NOT NULL,
    difference numeric(14,4),
    measured_by_user_id bigint,
    note text,
    measured_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE tank_measurements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE tank_measurements_id_seq OWNED BY tank_measurements.id;

ALTER TABLE ONLY tank_measurements ALTER COLUMN id SET DEFAULT nextval('tank_measurements_id_seq'::regclass);

ALTER TABLE ONLY tank_measurements
    ADD CONSTRAINT tank_measurements_pkey PRIMARY KEY (id);

CREATE INDEX tank_measurements_material_id_index ON tank_measurements USING btree (material_id);

CREATE INDEX tank_measurements_storage_unit_id_index ON tank_measurements USING btree (storage_unit_id);

ALTER TABLE ONLY tank_measurements
    ADD CONSTRAINT tank_measurements_definition_id_foreign FOREIGN KEY (production_product_attribute_definition_id)
        REFERENCES production_product_attribute_definitions(id) ON DELETE RESTRICT;

ALTER TABLE ONLY tank_measurements
    ADD CONSTRAINT tank_measurements_material_id_foreign FOREIGN KEY (material_id)
        REFERENCES materials(id) ON DELETE RESTRICT;

ALTER TABLE ONLY tank_measurements
    ADD CONSTRAINT tank_measurements_measured_by_user_id_foreign FOREIGN KEY (measured_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY tank_measurements
    ADD CONSTRAINT tank_measurements_storage_unit_id_foreign FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE RESTRICT;
