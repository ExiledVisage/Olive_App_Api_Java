-- Laravel: 2026_09_18_100001_add_source_storage_unit_to_finished_product_runs.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

alter table "finished_product_runs" add column "source_storage_unit_id" bigint null;

alter table "finished_product_runs"
    add constraint "finished_product_runs_source_storage_unit_id_foreign"
        foreign key ("source_storage_unit_id")
        references "storage_units" ("id")
        on delete set null;
