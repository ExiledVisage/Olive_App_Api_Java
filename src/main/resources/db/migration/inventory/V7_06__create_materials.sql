-- materials: materyaller: parti ciktilari, tank materyalleri, tortu

CREATE TABLE materials (
    id bigint NOT NULL,
    production_batch_id bigint,
    production_product_type_id bigint NOT NULL,
    parent_material_id bigint,
    origin_type character varying(50) NOT NULL,
    quantity numeric(14,4) DEFAULT '0'::numeric NOT NULL,
    unit character varying(255) NOT NULL,
    status character varying(30) DEFAULT 'active'::character varying NOT NULL,
    current_location_type character varying(50),
    current_location_id bigint,
    source_production_stage_id bigint,
    created_by_batch_process_id bigint,
    closed_by_batch_process_id bigint,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT materials_unit_check CHECK (unit IN ('KG', 'LITRE'))
);

CREATE SEQUENCE materials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE materials_id_seq OWNED BY materials.id;

ALTER TABLE ONLY materials ALTER COLUMN id SET DEFAULT nextval('materials_id_seq'::regclass);

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_pkey PRIMARY KEY (id);

CREATE INDEX materials_closed_by_batch_process_id_index ON materials USING btree (closed_by_batch_process_id);

CREATE INDEX materials_created_by_batch_process_id_index ON materials USING btree (created_by_batch_process_id);

CREATE INDEX materials_current_location_type_current_location_id_index ON materials
    USING btree (current_location_type, current_location_id);

CREATE INDEX materials_origin_type_index ON materials USING btree (origin_type);

CREATE INDEX materials_parent_material_id_index ON materials USING btree (parent_material_id);

CREATE INDEX materials_production_batch_id_status_index ON materials USING btree (production_batch_id, status);

CREATE INDEX materials_production_product_type_id_index ON materials USING btree (production_product_type_id);

CREATE INDEX materials_source_production_stage_id_index ON materials USING btree (source_production_stage_id);

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_parent_material_id_foreign FOREIGN KEY (parent_material_id)
        REFERENCES materials(id) ON DELETE SET NULL;

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_production_product_type_id_foreign FOREIGN KEY (production_product_type_id)
        REFERENCES production_product_types(id) ON DELETE RESTRICT;

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_source_production_stage_id_foreign FOREIGN KEY (source_production_stage_id)
        REFERENCES production_stages(id) ON DELETE SET NULL;
