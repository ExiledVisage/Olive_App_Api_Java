-- storage_unit_allowed_production_products: tanka girebilen urunler

CREATE TABLE storage_unit_allowed_production_products (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE storage_unit_allowed_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_unit_allowed_production_products_id_seq OWNED BY storage_unit_allowed_production_products.id;

ALTER TABLE ONLY storage_unit_allowed_production_products ALTER COLUMN id SET DEFAULT nextval('storage_unit_allowed_production_products_id_seq'::regclass);

ALTER TABLE ONLY storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_storage_unit_id_produc UNIQUE (storage_unit_id, production_product_id);

ALTER TABLE ONLY storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_production_product_id_ FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_storage_unit_id_foreig FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE CASCADE;
