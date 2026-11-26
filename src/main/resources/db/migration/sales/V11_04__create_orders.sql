-- orders: siparisler; source siparisin geldigi yer (elle ya da site/pazar yeri), external_ref oradaki numara
-- (docs/INTEGRATIONS.md)

CREATE TABLE orders (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    delivery_address_id bigint NOT NULL,
    created_by_user_id bigint,
    status character varying(30) DEFAULT 'draft'::character varying NOT NULL,
    order_date date NOT NULL,
    note text,
    source character varying(20) DEFAULT 'manual'::character varying NOT NULL,
    external_ref character varying(100),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT orders_source_check CHECK (source IN ('manual', 'woocommerce', 'trendyol', 'hepsiburada'))
);

CREATE SEQUENCE orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE orders_id_seq OWNED BY orders.id;

ALTER TABLE ONLY orders ALTER COLUMN id SET DEFAULT nextval('orders_id_seq'::regclass);

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

CREATE INDEX orders_customer_id_index ON orders USING btree (customer_id);

CREATE INDEX orders_order_date_index ON orders USING btree (order_date);

CREATE INDEX orders_status_index ON orders USING btree (status);

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_created_by_user_id_foreign FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT;

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_delivery_address_id_foreign FOREIGN KEY (delivery_address_id)
        REFERENCES customer_addresses(id) ON DELETE RESTRICT;

-- Bu tablodan once olusan tablolarin (finished_product_stock_movements) bu tabloya foreign key'leri

ALTER TABLE ONLY finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_order_id_foreign FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE SET NULL;
