-- Laravel: 2026_06_24_100006_create_finished_product_runs_table.php

create table "finished_product_runs" (
    "id" bigserial not null primary key,
    "finished_product_id" bigint not null,
    "finished_product_recipe_id" bigint not null,
    "quantity" integer not null,
    "status" varchar(30) not null default 'planned',
    "note" text null,
    "started_at" timestamp(0) without time zone null,
    "completed_at" timestamp(0) without time zone null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_runs"
    add constraint "finished_product_runs_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete restrict;

alter table "finished_product_runs"
    add constraint "finished_product_runs_finished_product_recipe_id_foreign"
        foreign key ("finished_product_recipe_id")
        references "finished_product_recipes" ("id")
        on delete restrict;

create index "finished_product_runs_finished_product_id_index"
    on "finished_product_runs" ("finished_product_id");

create index "finished_product_runs_status_index" on "finished_product_runs" ("status");
