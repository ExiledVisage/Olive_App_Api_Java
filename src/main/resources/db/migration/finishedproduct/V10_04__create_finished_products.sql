-- finished_products: bitmis urunler (tarif + ambalaj); barkod ve SKU pazar yeri ve sitedeki urunle eslestirir
-- (docs/INTEGRATIONS.md)

CREATE TABLE finished_products (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    volume_litre numeric(10,4) NOT NULL,
    total_stock integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    barcode character varying(64),
    sku character varying(64),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_products_id_seq OWNED BY finished_products.id;

ALTER TABLE ONLY finished_products ALTER COLUMN id SET DEFAULT nextval('finished_products_id_seq'::regclass);

ALTER TABLE ONLY finished_products
    ADD CONSTRAINT finished_products_pkey PRIMARY KEY (id);

CREATE INDEX finished_products_finished_product_recipe_id_index ON finished_products
    USING btree (finished_product_recipe_id);

CREATE INDEX finished_products_is_active_index ON finished_products USING btree (is_active);

CREATE UNIQUE INDEX finished_products_barcode_unique ON finished_products USING btree (barcode)
    WHERE barcode IS NOT NULL;

CREATE UNIQUE INDEX finished_products_sku_unique ON finished_products USING btree (sku) WHERE sku IS NOT NULL;

ALTER TABLE ONLY finished_products
    ADD CONSTRAINT finished_products_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id)
        REFERENCES finished_product_recipes(id) ON DELETE RESTRICT;
