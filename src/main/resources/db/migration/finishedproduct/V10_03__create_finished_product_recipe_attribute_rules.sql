-- finished_product_recipe_attribute_rules: tarifin attribute kurallari

CREATE TABLE finished_product_recipe_attribute_rules (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    value numeric(14,4) NOT NULL,
    min_deviation numeric(14,4),
    max_deviation numeric(14,4),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_recipe_attribute_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_recipe_attribute_rules_id_seq OWNED BY finished_product_recipe_attribute_rules.id;

ALTER TABLE ONLY finished_product_recipe_attribute_rules ALTER COLUMN id SET DEFAULT nextval('finished_product_recipe_attribute_rules_id_seq'::regclass);

ALTER TABLE ONLY finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY finished_product_recipe_attribute_rules
    ADD CONSTRAINT fpr_attr_rule_unique UNIQUE (finished_product_recipe_id, attribute_definition_id);

ALTER TABLE ONLY finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_attribute_definition_id FOREIGN KEY (attribute_definition_id)
        REFERENCES attribute_definitions(id) ON DELETE RESTRICT;

ALTER TABLE ONLY finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_finished_product_recipe FOREIGN KEY (finished_product_recipe_id)
        REFERENCES finished_product_recipes(id) ON DELETE CASCADE;
