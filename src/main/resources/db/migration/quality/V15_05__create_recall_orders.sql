-- recall_orders: geri cagirmanin etkiledigi siparisler: kesin / olasi, aranma, geri gelen adet

CREATE TABLE recall_orders (
    id bigint NOT NULL,
    recall_id bigint NOT NULL,
    order_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer NOT NULL,
    certainty character varying(10) NOT NULL,
    contacted_at timestamp(0) without time zone,
    returned_quantity integer,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT recall_orders_certainty_check CHECK (certainty IN ('definite', 'possible'))
);

CREATE SEQUENCE recall_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE recall_orders_id_seq OWNED BY recall_orders.id;

ALTER TABLE ONLY recall_orders ALTER COLUMN id SET DEFAULT nextval('recall_orders_id_seq'::regclass);

ALTER TABLE ONLY recall_orders
    ADD CONSTRAINT recall_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY recall_orders
    ADD CONSTRAINT recall_orders_unique UNIQUE (recall_id, order_id, finished_product_id);

ALTER TABLE ONLY recall_orders
    ADD CONSTRAINT recall_orders_recall_fk FOREIGN KEY (recall_id) REFERENCES recalls(id) ON DELETE CASCADE;

ALTER TABLE ONLY recall_orders
    ADD CONSTRAINT recall_orders_order_fk FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY recall_orders
    ADD CONSTRAINT recall_orders_product_fk FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE CASCADE;
