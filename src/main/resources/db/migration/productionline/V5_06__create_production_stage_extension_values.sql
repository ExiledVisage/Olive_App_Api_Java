-- production_stage_extension_values: asama uzantisinin ayar degerleri

CREATE TABLE production_stage_extension_values (
    id bigint NOT NULL,
    production_stage_extension_id bigint NOT NULL,
    stage_extension_type_field_id bigint NOT NULL,
    value_boolean boolean,
    value_integer bigint,
    value_decimal numeric(14,4),
    value_text character varying(255),
    value_json jsonb,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_stage_extension_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_stage_extension_values_id_seq OWNED BY production_stage_extension_values.id;

ALTER TABLE ONLY production_stage_extension_values ALTER COLUMN id SET DEFAULT nextval('production_stage_extension_values_id_seq'::regclass);

ALTER TABLE ONLY production_stage_extension_values
    ADD CONSTRAINT prod_stage_ext_value_unique UNIQUE (production_stage_extension_id, stage_extension_type_field_id);

ALTER TABLE ONLY production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_production_stage_extension_id FOREIGN KEY (production_stage_extension_id)
        REFERENCES production_stage_extensions(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_stage_extension_type_field_id FOREIGN KEY (stage_extension_type_field_id)
        REFERENCES stage_extension_type_fields(id) ON DELETE RESTRICT;
