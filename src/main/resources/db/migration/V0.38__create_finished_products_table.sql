-- Laravel: 2026_06_24_100004_create_finished_products_table.php

create table "finished_products" (
    "id" bigserial not null primary key,
    "finished_product_recipe_id" bigint not null,
    "name" varchar(255) not null,
    "volume_litre" decimal(10, 4) not null,
    "total_stock" integer not null default '0',
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_products"
    add constraint "finished_products_finished_product_recipe_id_foreign"
        foreign key ("finished_product_recipe_id")
        references "finished_product_recipes" ("id")
        on delete restrict;

create index "finished_products_finished_product_recipe_id_index"
    on "finished_products" ("finished_product_recipe_id");

create index "finished_products_is_active_index" on "finished_products" ("is_active");
