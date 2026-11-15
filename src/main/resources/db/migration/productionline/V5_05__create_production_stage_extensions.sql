-- production_stage_extensions: asamaya bagli uzantilar

CREATE TABLE production_stage_extensions (
    id bigint NOT NULL,
    production_stage_id bigint NOT NULL,
    stage_extension_type_id bigint NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    is_enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_stage_extensions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_stage_extensions_id_seq OWNED BY production_stage_extensions.id;

ALTER TABLE ONLY production_stage_extensions ALTER COLUMN id SET DEFAULT nextval('production_stage_extensions_id_seq'::regclass);

ALTER TABLE ONLY production_stage_extensions
    ADD CONSTRAINT prod_stage_ext_unique UNIQUE (production_stage_id, stage_extension_type_id);

ALTER TABLE ONLY production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_production_stage_id_foreign FOREIGN KEY (production_stage_id)
        REFERENCES production_stages(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_stage_extension_type_id_foreign FOREIGN KEY (stage_extension_type_id)
        REFERENCES stage_extension_types(id) ON DELETE RESTRICT;
