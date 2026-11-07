-- Laravel: 2026_10_05_100000_add_density_to_production_product_types.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

alter table "production_product_types" add column "density_kg_per_litre" decimal(6, 4) null;
