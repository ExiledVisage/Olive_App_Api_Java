-- storage_movements: tank hareketleri; transfer referansi sirasi da burada

CREATE TABLE storage_movements (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    material_id bigint NOT NULL,
    batch_process_id bigint,
    counterparty_storage_unit_id bigint,
    source_type character varying(50) NOT NULL,
    movement_type character varying(50) NOT NULL,
    transfer_reference character varying(255),
    quantity numeric(14,2) NOT NULL,
    unit character varying(255) NOT NULL,
    note text,
    moved_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    finished_product_run_id bigint,
    source_material_id bigint,
    CONSTRAINT storage_movements_unit_check CHECK (unit IN ('KG', 'LITRE'))
);

CREATE SEQUENCE storage_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_movements_id_seq OWNED BY storage_movements.id;

CREATE SEQUENCE transfer_references_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE ONLY storage_movements ALTER COLUMN id SET DEFAULT nextval('storage_movements_id_seq'::regclass);

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_pkey PRIMARY KEY (id);

CREATE INDEX storage_movements_batch_process_id_index ON storage_movements USING btree (batch_process_id);

CREATE INDEX storage_movements_material_id_index ON storage_movements USING btree (material_id);

CREATE INDEX storage_movements_moved_at_index ON storage_movements USING btree (moved_at);

CREATE INDEX storage_movements_source_material_id_index ON storage_movements USING btree (source_material_id);

CREATE INDEX storage_movements_source_type_index ON storage_movements USING btree (source_type);

CREATE INDEX storage_movements_storage_unit_id_movement_type_index ON storage_movements
    USING btree (storage_unit_id, movement_type);

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_counterparty_storage_unit_id_foreign FOREIGN KEY (counterparty_storage_unit_id)
        REFERENCES storage_units(id) ON DELETE SET NULL;

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_material_id_foreign FOREIGN KEY (material_id)
        REFERENCES materials(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_source_material_id_foreign FOREIGN KEY (source_material_id)
        REFERENCES materials(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_storage_unit_id_foreign FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE RESTRICT;
