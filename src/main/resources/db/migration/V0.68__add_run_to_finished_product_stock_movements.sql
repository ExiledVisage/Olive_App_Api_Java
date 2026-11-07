-- Laravel: 2026_10_05_100002_add_run_to_finished_product_stock_movements.php

alter table "finished_product_stock_movements" add column "finished_product_run_id" bigint null;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_finished_product_run_id_foreign"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete set null;

create index "fp_stock_mov_run_idx"
    on "finished_product_stock_movements" ("finished_product_run_id");
