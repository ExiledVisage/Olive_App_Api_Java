-- finished_product_storage_slot_stocks: raf stoklari

CREATE TABLE finished_product_storage_slot_stocks (
    id bigint NOT NULL,
    finished_product_storage_slot_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_storage_slot_stocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_storage_slot_stocks_id_seq OWNED BY finished_product_storage_slot_stocks.id;

ALTER TABLE ONLY finished_product_storage_slot_stocks ALTER COLUMN id SET DEFAULT nextval('finished_product_storage_slot_stocks_id_seq'::regclass);

ALTER TABLE ONLY finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_storage_slot_stocks
    ADD CONSTRAINT fp_slot_stock_unique UNIQUE (finished_product_storage_slot_id, finished_product_id);

CREATE INDEX finished_product_storage_slot_stocks_finished_product_id_index ON finished_product_storage_slot_stocks
    USING btree (finished_product_id);

ALTER TABLE ONLY finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_finished_product_id_foreig FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_finished_product_storage_s FOREIGN KEY (finished_product_storage_slot_id)
        REFERENCES finished_product_storage_slots(id) ON DELETE CASCADE;
