-- Laravel: 2026_10_05_100001_add_default_density_to_production_products.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

alter table "production_products" add column "default_density_kg_per_litre" decimal(6, 4) null;
