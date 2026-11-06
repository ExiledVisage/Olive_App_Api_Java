# Laravel Referans Sistemi

Java tarafının Laravel ile birebir aynı davrandığını kontrol etmek için kullanılan sabit veri ve araçlar.
Faz 0'da üretildi (bkz. [MIGRATION_ROADMAP.md](../docs/MIGRATION_ROADMAP.md)).

Kaynak: `Olive_Production_Tracking_App_Back` `dev` branch'i, `ba61990`. Laravel 12, PostgreSQL 17.11.

## Dosyalar

| Dosya | Ne işe yarar | Git'te |
|---|---|---|
| `reference-data.sql` | Dondurulmuş demo verinin tam dökümü (şema + veri). Karşılaştırmalar hep bunun üzerinde yapılır | Evet |
| `schema.sql` | Sadece şema (73 tablo). Faz 1'de Flyway `V1__baseline.sql` buradan çıkarılacak | Evet |
| `restore.sh` | Laravel'in dev veritabanını `reference-data.sql`'e geri döndürür | Evet |
| `capture_responses.py` | Tüm GET endpoint'lerini admin olarak çağırıp cevapları `responses/`'a yazar | Evet |
| `responses/` | Yakalanan cevaplar (84 istek, ~21 MB) | Hayır, yeniden üretilir |

Endpoint envanteri: [docs/laravel-reference/ENDPOINTS.md](../docs/laravel-reference/ENDPOINTS.md)

## Kullanım

```bash
# Laravel ayakta olmalı (Olive_Production_Tracking_App_Back/olive_oil_back içinde: docker compose up -d)
LARAVEL=../Olive_Production_Tracking_App_Back/olive_oil_back

reference/restore.sh "$LARAVEL"                       # veriyi dondurulmuş hale döndür
python -I reference/capture_responses.py "$LARAVEL"   # cevapları yakala
```

`restore.sh` hedef veritabanındaki her şeyi siler. Sadece `COMPOSE_PROJECT_NAME` içinde `dev` geçen ortamlarda çalışır.

## Demo veri

`seed-demo.sh` ile 2026-11-06'da üretildi:

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

Demo kullanıcılar (hepsinin şifresi `123`): `admin@olivamore.com`, `operator1@olivamore.com`, `operator2@olivamore.com`, `planner@olivamore.com` ve 3 personel.

## Bulgular (Java tarafı için önemli)

### Veri seed gününe göre üretiliyor
Demo seeder veriyi seed günü (2026-11-06) bitecek şekilde son bir yıla yayıyor. Dökümde tarihler sabit, ama "bugün", "son 30 gün" gibi şimdiki zamana göre hesap yapan endpoint'lerin cevabı günden güne değişir.
**Sonuç:** Asıl karşılaştırma, aynı veri üzerinde **aynı anda** Laravel'e ve Java'ya istek atılarak yapılmalı. `responses/` altındaki dosyalar belge ve ilk kontrol içindir.

### Rollerin izin listesinin sırası rastgele
`/api/roles` (ve roller dönen her yer) rollerin izinlerini `ORDER BY` olmadan döndürüyor, sıra çağrıdan çağrıya değişiyor. Bu dosya dışındaki 83 cevap tekrar çalıştırmada birebir aynı çıktı.
**Sonuç:** Karşılaştırmada izin listeleri sırasız küme olarak karşılaştırılmalı.

### Sayfalama `per_page`'e bağlı
`ResolvesPerPage`: `per_page` verilmezse liste endpoint'leri **sayfalamaz**, bütün kayıtları sadece `{"data": [...]}` olarak döner (`links`/`meta` yok). `per_page` (1–200) verilince Laravel'in sayfalı formatı (`data`, `links`, `meta`) döner.
**Sonuç:** Faz 1'deki ortak pagination iki davranışı da birebir kopyalamalı.

### Büyük cevaplar
`/api/production-batches` sayfasız haliyle 273 partiyi iç içe dönüyor, cevap ~18,7 MB. Diğer büyükler: `/api/orders` ~750 KB, `/api/finished-product-storages/{id}/movements` ~450 KB.
Birebir taşınır, iyileştirme Geçiş Sonrası'na kalır.

### Zorunlu query parametreleri
`analytics/customers`, `analytics/customers/{id}`, `analytics/suppliers`, `analytics/yield`, `analytics/query` ve `production-batches/calendar` `from` ve `to` istiyor (`analytics/query` ayrıca `metric` ve `group`). Parametresiz hali (422, validation hata formatı referansı) ve parametreli hali ayrı ayrı kaydedildi.

### Login cevabı
`POST /api/auth/login` → `{"message": "Login successful.", "token": "<sanctum>", "user": {...UserResource}}`. `GET /api/user` ise UserResource değil, User modelini olduğu gibi (`$hidden` hariç) döner.

### App release
Bu bilgisayarda yayınlanmış APK olmadığı için `/api/app/version` ve `/api/app/download` 404 döner (`{"message": "Yayinlanmis surum yok."}`).

## Laravel testleri
Docker'da çalıştırıldı: **444 test, 2326 assertion, hepsi geçti** (60 sn). Testler SQLite kullanıyor ve imaja girmiyor, bu yüzden geçici bir konteynerde çalıştırılır:

```bash
cd ../Olive_Production_Tracking_App_Back/olive_oil_back
MSYS_NO_PATHCONV=1 docker run --rm --user root -v "$(pwd -W):/work" -v olive-test-vendor:/work/vendor -w /work \
  olivamore-dev-api sh -c "composer install --no-interaction -q && php artisan test"
```
