-- production_line_input_production_products: hattin girdi urunleri

CREATE TABLE production_line_input_production_products (
    id bigint NOT NULL,
    production_line_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    production_product_type_id bigint
);

CREATE SEQUENCE production_line_input_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_line_input_production_products_id_seq OWNED BY production_line_input_production_products.id;

ALTER TABLE ONLY production_line_input_production_products ALTER COLUMN id SET DEFAULT nextval('production_line_input_production_products_id_seq'::regclass);

ALTER TABLE ONLY production_line_input_production_products
    ADD CONSTRAINT production_line_input_product_type_unique UNIQUE (production_line_id, production_product_id);

ALTER TABLE ONLY production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_line_input_production_products
    ADD CONSTRAINT prod_line_input_product_type_fk FOREIGN KEY (production_product_type_id)
        REFERENCES production_product_types(id) ON DELETE RESTRICT;

ALTER TABLE ONLY production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_production_line_id_fo FOREIGN KEY (production_line_id)
        REFERENCES production_lines(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_production_product_id FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE RESTRICT;
