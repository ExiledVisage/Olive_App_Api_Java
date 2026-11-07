-- Laravel: 2026_10_06_100000_add_order_to_finished_product_stock_movements.php

alter table "finished_product_stock_movements" add column "order_id" bigint null;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_order_id_foreign"
        foreign key ("order_id")
        references "orders" ("id")
        on delete set null;

create index "fp_stock_mov_order_idx" on "finished_product_stock_movements" ("order_id");
