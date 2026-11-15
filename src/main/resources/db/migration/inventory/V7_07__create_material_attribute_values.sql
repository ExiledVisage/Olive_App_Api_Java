-- material_attribute_values: materyallerin attribute degerleri

CREATE TABLE material_attribute_values (
    id bigint NOT NULL,
    material_id bigint NOT NULL,
    production_product_attribute_definition_id bigint NOT NULL,
    value_boolean boolean,
    value_integer bigint,
    value_decimal numeric(14,4),
    value_text character varying(255),
    value_json jsonb,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE material_attribute_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE material_attribute_values_id_seq OWNED BY material_attribute_values.id;

ALTER TABLE ONLY material_attribute_values ALTER COLUMN id SET DEFAULT nextval('material_attribute_values_id_seq'::regclass);

ALTER TABLE ONLY material_attribute_values
    ADD CONSTRAINT material_attr_value_unique UNIQUE (material_id, production_product_attribute_definition_id);

ALTER TABLE ONLY material_attribute_values
    ADD CONSTRAINT material_attribute_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY material_attribute_values
    ADD CONSTRAINT material_attribute_values_material_id_foreign FOREIGN KEY (material_id)
        REFERENCES materials(id) ON DELETE CASCADE;

ALTER TABLE ONLY material_attribute_values
    ADD CONSTRAINT material_attribute_values_production_product_attribute_definiti FOREIGN KEY (production_product_attribute_definition_id)
        REFERENCES production_product_attribute_definitions(id) ON DELETE RESTRICT;
