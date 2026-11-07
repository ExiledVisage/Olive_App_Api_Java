-- Laravel: 2026_03_23_223223_create_material_attribute_values_table.php

create table "material_attribute_values" (
    "id" bigserial not null primary key,
    "material_id" bigint not null,
    "production_product_attribute_definition_id" bigint not null,
    "value_boolean" boolean null,
    "value_integer" bigint null,
    "value_decimal" decimal(14, 4) null,
    "value_text" varchar(255) null,
    "value_json" json null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "material_attribute_values"
    add constraint "material_attribute_values_material_id_foreign"
        foreign key ("material_id")
        references "materials" ("id")
        on delete cascade;

alter table "material_attribute_values"
    add constraint "material_attribute_values_production_product_attribute_definition_id_foreign"
        foreign key ("production_product_attribute_definition_id")
        references "production_product_attribute_definitions" ("id")
        on delete restrict;

alter table "material_attribute_values"
    add constraint "material_attr_value_unique"
        unique ("material_id", "production_product_attribute_definition_id");
