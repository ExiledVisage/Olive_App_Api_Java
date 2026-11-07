-- Laravel: 2026_06_24_100002_create_finished_product_recipe_contents_table.php

create table "finished_product_recipe_contents" (
    "id" bigserial not null primary key,
    "finished_product_recipe_id" bigint not null,
    "production_product_type_id" bigint not null,
    "ratio" decimal(5, 4) not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_recipe_contents"
    add constraint "finished_product_recipe_contents_finished_product_recipe_id_foreign"
        foreign key ("finished_product_recipe_id")
        references "finished_product_recipes" ("id")
        on delete cascade;

alter table "finished_product_recipe_contents"
    add constraint "finished_product_recipe_contents_production_product_type_id_foreign"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;

alter table "finished_product_recipe_contents"
    add constraint "fpr_content_unique"
        unique ("finished_product_recipe_id", "production_product_type_id");
