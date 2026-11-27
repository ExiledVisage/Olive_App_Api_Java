-- order_items: siparis kalemleri; unit_price KDV dahil, indirim sonrasi birim fiyat (bos: urunun liste fiyati
-- faturada kullanilir), vat_rate kalem yazilirken urunden alinir (docs/INVOICES.md)

CREATE TABLE order_items (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2),
    vat_rate integer NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT order_items_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT order_items_vat_rate_check CHECK (vat_rate IN (0, 1, 10, 20))
);

CREATE SEQUENCE order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE order_items_id_seq OWNED BY order_items.id;

ALTER TABLE ONLY order_items ALTER COLUMN id SET DEFAULT nextval('order_items_id_seq'::regclass);

ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_item_unique UNIQUE (order_id, finished_product_id);

ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_order_id_foreign FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
