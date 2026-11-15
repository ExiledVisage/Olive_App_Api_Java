-- finished_product_recipes: dolum tarifleri

CREATE TABLE finished_product_recipes (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_recipes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_recipes_id_seq OWNED BY finished_product_recipes.id;

ALTER TABLE ONLY finished_product_recipes ALTER COLUMN id SET DEFAULT nextval('finished_product_recipes_id_seq'::regclass);

ALTER TABLE ONLY finished_product_recipes
    ADD CONSTRAINT finished_product_recipes_pkey PRIMARY KEY (id);

CREATE INDEX finished_product_recipes_is_active_index ON finished_product_recipes USING btree (is_active);

CREATE INDEX finished_product_recipes_name_version_index ON finished_product_recipes USING btree (name, version);
