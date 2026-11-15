-- packaging_lots: ambalaj lotlari

CREATE TABLE packaging_lots (
    id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    product_supplier_id bigint,
    lot_code character varying(100) NOT NULL,
    received_at timestamp(0) without time zone NOT NULL,
    quantity_received integer NOT NULL,
    quantity_remaining integer NOT NULL,
    status character varying(20) DEFAULT 'active'::character varying NOT NULL,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE packaging_lots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE packaging_lots_id_seq OWNED BY packaging_lots.id;

ALTER TABLE ONLY packaging_lots ALTER COLUMN id SET DEFAULT nextval('packaging_lots_id_seq'::regclass);

ALTER TABLE ONLY packaging_lots
    ADD CONSTRAINT packaging_lots_pkey PRIMARY KEY (id);

ALTER TABLE ONLY packaging_lots
    ADD CONSTRAINT pkg_lots_item_lot_unique UNIQUE (packaging_item_id, lot_code);

CREATE INDEX pkg_lots_item_status_idx ON packaging_lots USING btree (packaging_item_id, status);

CREATE INDEX pkg_lots_received_idx ON packaging_lots USING btree (received_at);

CREATE INDEX pkg_lots_supplier_idx ON packaging_lots USING btree (product_supplier_id);

ALTER TABLE ONLY packaging_lots
    ADD CONSTRAINT packaging_lots_packaging_item_id_foreign FOREIGN KEY (packaging_item_id)
        REFERENCES packaging_items(id) ON DELETE RESTRICT;

ALTER TABLE ONLY packaging_lots
    ADD CONSTRAINT packaging_lots_product_supplier_id_foreign FOREIGN KEY (product_supplier_id)
        REFERENCES product_suppliers(id) ON DELETE SET NULL;
