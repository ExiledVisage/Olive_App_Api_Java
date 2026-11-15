-- finished_product_recipe_contents: tarifin icerik oranlari

CREATE TABLE finished_product_recipe_contents (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    ratio numeric(5,4) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_recipe_contents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_recipe_contents_id_seq OWNED BY finished_product_recipe_contents.id;

ALTER TABLE ONLY finished_product_recipe_contents ALTER COLUMN id SET DEFAULT nextval('finished_product_recipe_contents_id_seq'::regclass);

ALTER TABLE ONLY finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_recipe_contents
    ADD CONSTRAINT fpr_content_unique UNIQUE (finished_product_recipe_id, production_product_type_id);

ALTER TABLE ONLY finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_finished_product_recipe_id_for FOREIGN KEY (finished_product_recipe_id)
        REFERENCES finished_product_recipes(id) ON DELETE CASCADE;

ALTER TABLE ONLY finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_production_product_type_id_for FOREIGN KEY (production_product_type_id)
        REFERENCES production_product_types(id) ON DELETE RESTRICT;
