# Referans Veri

Testlerin ve sunucu geçişinin kullandığı sabit veritabanı dökümleri. Laravel sisteminin 2026-11-06 tarihli demo
verisinden (`Olive_Production_Tracking_App_Back`, `dev`, `ba61990`, PostgreSQL 17.11) üretildi.

## Dosyalar

| Dosya | Ne işe yarar |
|---|---|
| `reference-data.sql` | Laravel veritabanının tam dökümü (şema + veri). `BaselineSchemaIT` ve `LaravelDatabaseUpgradeIT` canlı geçişi bunun üzerinde dener |
| `schema.sql` | Laravel'in şeması. `deploy/compare-schema.sh` canlı şemayı bununla karşılaştırır (bkz. `docs/GO_LIVE.md`) |
| `seed-data.sql` | Yalnızca veri, Java şemasına çevrilmiş. `--seed` komutu ve integration testleri bunu yükler |

## `seed-data.sql`'i yeniden üretmek

`reference-data.sql`'i boş bir Postgres 17'ye yükleyip Flyway ile V2 ve sonrasını uygulayın (baseline 1), sonra
`pg_dump --data-only --no-owner --no-privileges` alın. `materials.parent_material_id` kendi tablosuna bakan bir foreign
key; pg_dump bunun için uyarı verir. Dökümde hiçbir kayıt kendinden sonraki bir id'yi göstermediği için yükleme FK
kontrolleri açıkken sorunsuz geçer (`BaselineSchemaIT` doğrular).

## Demo veri

Veri, seed günü (2026-11-06) bitecek şekilde son bir yıla yayılmıştır. "Bugün", "son 30 gün" gibi şimdiki zamana göre
hesap yapan uçların senaryoları bu yüzden uygulama saatini sabit bir ana kaydırır (`ScenarioReplay.clockAt`).

| Tablo | Kayıt |
|---|---|
| production_batches | 273 |
| batch_processes | 3276 |
| materials | 2091 |
| storage_movements | 2815 |
| finished_product_runs | 389 |
| finished_product_stock_movements | 2782 |
| orders | 335 |
| order_items | 752 |
| users | 7 |

Demo kullanıcılar (hepsinin şifresi `123`): `admin@olivamore.com`, `operator1@olivamore.com`,
`operator2@olivamore.com`, `planner@olivamore.com` ve 3 personel.
