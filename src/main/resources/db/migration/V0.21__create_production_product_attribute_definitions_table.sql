-- Laravel: 2026_03_23_223221_create_production_product_attribute_definitions_table.php

create table "attribute_definitions" (
    "id" bigserial not null primary key,
    "attribute_key" varchar(255) not null,
    "label" varchar(255) not null,
    "data_type" varchar(255) not null,
    "unit" varchar(255) null,
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "attribute_definitions"
    add constraint "attribute_definitions_attribute_key_unique" unique ("attribute_key");

create table "production_product_attribute_definitions" (
    "id" bigserial not null primary key,
    "production_product_id" bigint not null,
    "attribute_definition_id" bigint not null,
    "is_required" boolean not null default '0',
    "sort_order" integer not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_product_attribute_definitions"
    add constraint "production_product_attribute_definitions_production_product_id_foreign"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete cascade;

alter table "production_product_attribute_definitions"
    add constraint "production_product_attribute_definitions_attribute_definition_id_foreign"
        foreign key ("attribute_definition_id")
        references "attribute_definitions" ("id")
        on delete cascade;

alter table "production_product_attribute_definitions"
    add constraint "product_attr_def_unique"
        unique ("production_product_id", "attribute_definition_id");
