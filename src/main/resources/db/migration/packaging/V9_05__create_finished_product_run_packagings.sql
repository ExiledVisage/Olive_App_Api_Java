-- finished_product_run_packagings: dolumda kullanilan ambalaj lotlari

CREATE TABLE finished_product_run_packagings (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    packaging_lot_id bigint NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_run_packagings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_run_packagings_id_seq OWNED BY finished_product_run_packagings.id;

ALTER TABLE ONLY finished_product_run_packagings ALTER COLUMN id SET DEFAULT nextval('finished_product_run_packagings_id_seq'::regclass);

ALTER TABLE ONLY finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_run_packagings
    ADD CONSTRAINT fpr_pkg_run_lot_unique UNIQUE (finished_product_run_id, packaging_lot_id);

CREATE INDEX fpr_pkg_run_idx ON finished_product_run_packagings USING btree (finished_product_run_id);

ALTER TABLE ONLY finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_packaging_item_id_foreign FOREIGN KEY (packaging_item_id)
        REFERENCES packaging_items(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_packaging_lot_id_foreign FOREIGN KEY (packaging_lot_id)
        REFERENCES packaging_lots(id) ON DELETE RESTRICT;
