-- finished_product_storage_stocks: depo stoklari

CREATE TABLE finished_product_storage_stocks (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_storage_stocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_storage_stocks_id_seq OWNED BY finished_product_storage_stocks.id;

ALTER TABLE ONLY finished_product_storage_stocks ALTER COLUMN id SET DEFAULT nextval('finished_product_storage_stocks_id_seq'::regclass);

ALTER TABLE ONLY finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_storage_stocks
    ADD CONSTRAINT fp_storage_stock_unique UNIQUE (finished_product_storage_id, finished_product_id);

ALTER TABLE ONLY finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_finished_product_storage_id_for FOREIGN KEY (finished_product_storage_id)
        REFERENCES finished_product_storages(id) ON DELETE CASCADE;
