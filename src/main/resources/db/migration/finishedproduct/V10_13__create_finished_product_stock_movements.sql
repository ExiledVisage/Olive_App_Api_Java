-- finished_product_stock_movements: bitmis urun stok hareketleri

CREATE TABLE finished_product_stock_movements (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    finished_product_storage_slot_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    movement_type character varying(20) NOT NULL,
    quantity integer NOT NULL,
    counterparty_slot_id bigint,
    transfer_reference uuid,
    created_by_user_id bigint,
    note text,
    moved_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    finished_product_run_id bigint,
    order_id bigint
);

CREATE SEQUENCE finished_product_stock_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_stock_movements_id_seq OWNED BY finished_product_stock_movements.id;

ALTER TABLE ONLY finished_product_stock_movements ALTER COLUMN id SET DEFAULT nextval('finished_product_stock_movements_id_seq'::regclass);

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_pkey PRIMARY KEY (id);

CREATE INDEX finished_product_stock_movements_finished_product_id_moved_at_i ON finished_product_stock_movements
    USING btree (finished_product_id, moved_at);

CREATE INDEX finished_product_stock_movements_finished_product_storage_id_mo ON finished_product_stock_movements
    USING btree (finished_product_storage_id, moved_at);

CREATE INDEX finished_product_stock_movements_transfer_reference_index ON finished_product_stock_movements
    USING btree (transfer_reference);

CREATE INDEX fp_stock_mov_order_idx ON finished_product_stock_movements USING btree (order_id);

CREATE INDEX fp_stock_mov_run_idx ON finished_product_stock_movements USING btree (finished_product_run_id);

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_counterparty_slot_id_foreign FOREIGN KEY (counterparty_slot_id)
        REFERENCES finished_product_storage_slots(id) ON DELETE SET NULL;

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_created_by_user_id_foreign FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_run_id_foreig FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE SET NULL;

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_storage_id_fo FOREIGN KEY (finished_product_storage_id)
        REFERENCES finished_product_storages(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_storage_slot_ FOREIGN KEY (finished_product_storage_slot_id)
        REFERENCES finished_product_storage_slots(id) ON DELETE RESTRICT;
