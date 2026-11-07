-- Laravel: 2026_09_18_100002_add_finished_product_run_to_storage_movements.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

alter table "storage_movements" add column "finished_product_run_id" bigint null;

alter table "storage_movements"
    add constraint "storage_movements_finished_product_run_id_foreign"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete set null;
