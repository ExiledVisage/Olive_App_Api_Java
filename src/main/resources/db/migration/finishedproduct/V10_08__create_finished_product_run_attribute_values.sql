-- finished_product_run_attribute_values: dolumun attribute degerleri

CREATE TABLE finished_product_run_attribute_values (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    estimated_value numeric(14,4) NOT NULL,
    actual_value numeric(14,4),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_run_attribute_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_run_attribute_values_id_seq OWNED BY finished_product_run_attribute_values.id;

ALTER TABLE ONLY finished_product_run_attribute_values ALTER COLUMN id SET DEFAULT nextval('finished_product_run_attribute_values_id_seq'::regclass);

ALTER TABLE ONLY finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_run_attribute_values
    ADD CONSTRAINT fp_run_attr_value_unique UNIQUE (finished_product_run_id, attribute_definition_id);

ALTER TABLE ONLY finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_attribute_definition_id_f FOREIGN KEY (attribute_definition_id)
        REFERENCES attribute_definitions(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_finished_product_run_id_f FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE CASCADE;
