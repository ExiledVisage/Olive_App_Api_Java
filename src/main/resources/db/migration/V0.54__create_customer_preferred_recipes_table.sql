-- Laravel: 2026_09_18_100000_create_customer_preferred_recipes_table.php

create table "customer_preferred_recipes" (
    "id" bigserial not null primary key,
    "customer_id" bigint not null,
    "finished_product_recipe_id" bigint not null,
    "note" varchar(255) null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "customer_preferred_recipes"
    add constraint "customer_preferred_recipes_customer_id_foreign"
        foreign key ("customer_id")
        references "customers" ("id")
        on delete cascade;

alter table "customer_preferred_recipes"
    add constraint "customer_preferred_recipes_finished_product_recipe_id_foreign"
        foreign key ("finished_product_recipe_id")
        references "finished_product_recipes" ("id")
        on delete cascade;

alter table "customer_preferred_recipes"
    add constraint "customer_preferred_recipe_unique"
        unique ("customer_id", "finished_product_recipe_id");
