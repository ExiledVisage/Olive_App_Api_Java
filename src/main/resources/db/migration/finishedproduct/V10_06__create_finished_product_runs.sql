-- finished_product_runs: dolum kosulari

CREATE TABLE finished_product_runs (
    id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    quantity integer NOT NULL,
    status character varying(30) DEFAULT 'planned'::character varying NOT NULL,
    note text,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    source_storage_unit_id bigint
);

CREATE SEQUENCE finished_product_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_runs_id_seq OWNED BY finished_product_runs.id;

ALTER TABLE ONLY finished_product_runs ALTER COLUMN id SET DEFAULT nextval('finished_product_runs_id_seq'::regclass);

ALTER TABLE ONLY finished_product_runs
    ADD CONSTRAINT finished_product_runs_pkey PRIMARY KEY (id);

CREATE INDEX finished_product_runs_finished_product_id_index ON finished_product_runs USING btree (finished_product_id);

CREATE INDEX finished_product_runs_status_index ON finished_product_runs USING btree (status);

ALTER TABLE ONLY finished_product_runs
    ADD CONSTRAINT finished_product_runs_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_runs
    ADD CONSTRAINT finished_product_runs_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id)
        REFERENCES finished_product_recipes(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_runs
    ADD CONSTRAINT finished_product_runs_source_storage_unit_id_foreign FOREIGN KEY (source_storage_unit_id)
        REFERENCES storage_units(id) ON DELETE SET NULL;

-- Bu tablodan once olusan tablolarin (finished_product_run_packagings, packaging_lot_movements, storage_movements) bu tabloya foreign key'leri

ALTER TABLE ONLY finished_product_run_packagings
    ADD CONSTRAINT fpr_pkg_run_fk FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE CASCADE;

ALTER TABLE ONLY packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE SET NULL;

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE SET NULL;
