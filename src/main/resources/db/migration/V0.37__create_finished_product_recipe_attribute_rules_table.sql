-- Laravel: 2026_06_24_100003_create_finished_product_recipe_attribute_rules_table.php

create table "finished_product_recipe_attribute_rules" (
    "id" bigserial not null primary key,
    "finished_product_recipe_id" bigint not null,
    "attribute_definition_id" bigint not null,
    "value" decimal(14, 4) not null,
    "min_deviation" decimal(14, 4) null,
    "max_deviation" decimal(14, 4) null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_recipe_attribute_rules"
    add constraint "finished_product_recipe_attribute_rules_finished_product_recipe_id_foreign"
        foreign key ("finished_product_recipe_id")
        references "finished_product_recipes" ("id")
        on delete cascade;

alter table "finished_product_recipe_attribute_rules"
    add constraint "finished_product_recipe_attribute_rules_attribute_definition_id_foreign"
        foreign key ("attribute_definition_id")
        references "attribute_definitions" ("id")
        on delete restrict;

alter table "finished_product_recipe_attribute_rules"
    add constraint "fpr_attr_rule_unique"
        unique ("finished_product_recipe_id", "attribute_definition_id");
