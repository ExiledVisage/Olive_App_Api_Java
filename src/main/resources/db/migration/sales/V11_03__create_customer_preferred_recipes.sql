-- customer_preferred_recipes: musterinin tercih ettigi tarifler

CREATE TABLE customer_preferred_recipes (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    note character varying(255),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE customer_preferred_recipes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE customer_preferred_recipes_id_seq OWNED BY customer_preferred_recipes.id;

ALTER TABLE ONLY customer_preferred_recipes ALTER COLUMN id SET DEFAULT nextval('customer_preferred_recipes_id_seq'::regclass);

ALTER TABLE ONLY customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipe_unique UNIQUE (customer_id, finished_product_recipe_id);

ALTER TABLE ONLY customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_customer_id_foreign FOREIGN KEY (customer_id)
        REFERENCES customers(id) ON DELETE CASCADE;

ALTER TABLE ONLY customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id)
        REFERENCES finished_product_recipes(id) ON DELETE CASCADE;
