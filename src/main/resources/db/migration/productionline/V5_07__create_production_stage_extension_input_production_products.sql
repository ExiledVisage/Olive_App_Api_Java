-- production_stage_extension_input_production_products: asama uzantisinin girdi urunleri

CREATE TABLE production_stage_extension_input_production_products (
    id bigint NOT NULL,
    production_stage_extension_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_stage_extension_input_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_stage_extension_input_production_products_id_seq OWNED BY production_stage_extension_input_production_products.id;

ALTER TABLE ONLY production_stage_extension_input_production_products ALTER COLUMN id SET DEFAULT nextval('production_stage_extension_input_production_products_id_seq'::regclass);

ALTER TABLE ONLY production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_product_type_unique UNIQUE (production_stage_extension_id, production_product_id);

ALTER TABLE ONLY production_stage_extension_input_production_products
    ADD CONSTRAINT production_stage_extension_input_production_products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_ext_fk FOREIGN KEY (production_stage_extension_id)
        REFERENCES production_stage_extensions(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_product_fk FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE RESTRICT;
