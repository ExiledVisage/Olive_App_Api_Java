-- finished_product_components: bitmis urunun ambalaj bilesenleri

CREATE TABLE finished_product_components (
    id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_components_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_components_id_seq OWNED BY finished_product_components.id;

ALTER TABLE ONLY finished_product_components ALTER COLUMN id SET DEFAULT nextval('finished_product_components_id_seq'::regclass);

ALTER TABLE ONLY finished_product_components
    ADD CONSTRAINT finished_product_components_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_components
    ADD CONSTRAINT fp_component_unique UNIQUE (finished_product_id, packaging_item_id);

ALTER TABLE ONLY finished_product_components
    ADD CONSTRAINT finished_product_components_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE CASCADE;

ALTER TABLE ONLY finished_product_components
    ADD CONSTRAINT finished_product_components_packaging_item_id_foreign FOREIGN KEY (packaging_item_id)
        REFERENCES packaging_items(id) ON DELETE RESTRICT;
