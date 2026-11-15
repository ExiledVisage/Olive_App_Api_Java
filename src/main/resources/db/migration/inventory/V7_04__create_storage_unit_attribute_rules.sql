-- storage_unit_attribute_rules: tankin attribute kurallari

CREATE TABLE storage_unit_attribute_rules (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_attribute_definition_id bigint NOT NULL,
    min_value_decimal numeric(15,4),
    max_value_decimal numeric(15,4),
    apply_mode character varying(255) DEFAULT 'incoming_and_average'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT storage_unit_attribute_rules_apply_mode_check CHECK (apply_mode IN ('incoming_only', 'tank_average_only', 'incoming_and_average'))
);

CREATE SEQUENCE storage_unit_attribute_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_unit_attribute_rules_id_seq OWNED BY storage_unit_attribute_rules.id;

ALTER TABLE ONLY storage_unit_attribute_rules ALTER COLUMN id SET DEFAULT nextval('storage_unit_attribute_rules_id_seq'::regclass);

ALTER TABLE ONLY storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attr_rules_unique UNIQUE (storage_unit_id, production_product_attribute_definition_id);

ALTER TABLE ONLY storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_production_product_attribute_defin FOREIGN KEY (production_product_attribute_definition_id)
        REFERENCES production_product_attribute_definitions(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_storage_unit_id_foreign FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE CASCADE;
