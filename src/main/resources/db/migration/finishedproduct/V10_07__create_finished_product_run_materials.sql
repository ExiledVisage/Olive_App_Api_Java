-- finished_product_run_materials: dolumun tanktan cektigi materyaller

CREATE TABLE finished_product_run_materials (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    source_material_id bigint NOT NULL,
    quantity_used numeric(14,4) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_run_materials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_run_materials_id_seq OWNED BY finished_product_run_materials.id;

ALTER TABLE ONLY finished_product_run_materials ALTER COLUMN id SET DEFAULT nextval('finished_product_run_materials_id_seq'::regclass);

ALTER TABLE ONLY finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_pkey PRIMARY KEY (id);

CREATE INDEX finished_product_run_materials_finished_product_run_id_index ON finished_product_run_materials
    USING btree (finished_product_run_id);

CREATE INDEX finished_product_run_materials_source_material_id_index ON finished_product_run_materials
    USING btree (source_material_id);

ALTER TABLE ONLY finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE CASCADE;

ALTER TABLE ONLY finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_source_material_id_foreign FOREIGN KEY (source_material_id)
        REFERENCES materials(id) ON DELETE RESTRICT;
