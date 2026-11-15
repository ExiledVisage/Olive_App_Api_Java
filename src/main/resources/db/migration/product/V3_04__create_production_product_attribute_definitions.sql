-- production_product_attribute_definitions: kategoriye bagli attribute

CREATE TABLE production_product_attribute_definitions (
    id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_product_attribute_definitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_product_attribute_definitions_id_seq OWNED BY production_product_attribute_definitions.id;

ALTER TABLE ONLY production_product_attribute_definitions ALTER COLUMN id SET DEFAULT nextval('production_product_attribute_definitions_id_seq'::regclass);

ALTER TABLE ONLY production_product_attribute_definitions
    ADD CONSTRAINT product_attr_def_unique UNIQUE (production_product_id, attribute_definition_id);

ALTER TABLE ONLY production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_attribute_definition_i FOREIGN KEY (attribute_definition_id)
        REFERENCES attribute_definitions(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_production_product_id_ FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE CASCADE;
