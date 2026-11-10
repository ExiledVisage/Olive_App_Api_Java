# Laravel Referans Sistemi

Java tarafının Laravel ile birebir aynı davrandığını kontrol etmek için kullanılan sabit veri ve araçlar.
Faz 0'da üretildi (bkz. [MIGRATION_ROADMAP.md](../docs/MIGRATION_ROADMAP.md)).

Kaynak: `Olive_Production_Tracking_App_Back` `dev` branch'i, `ba61990`. Laravel 12, PostgreSQL 17.11.

## Dosyalar

| Dosya | Ne işe yarar | Git'te |
|---|---|---|
| `reference-data.sql` | Dondurulmuş demo verinin tam dökümü (şema + veri). Karşılaştırmalar hep bunun üzerinde yapılır | Evet |
| `schema.sql` | Sadece şema (73 tablo). Flyway migration'larının (`db/migration/`) kurduğu şemayla aynı (`deploy/compare-schema.sh`) | Evet |
| `seed-data.sql` | Sadece veri, Java şemasına (V5) çevrilmiş: Laravel altyapı tabloları yok, roller ve izinler yeni tablolarda. `--seed` komutu ve integration testleri bunu yükler | Evet |
| `restore.sh` | Laravel'in dev veritabanını `reference-data.sql`'e geri döndürür | Evet |
| `capture_responses.py` | Tüm GET endpoint'lerini admin olarak çağırıp cevapları `responses/`'a yazar | Evet |
| `capture_identity.py` | Faz 2: Laravel'in kimlik/yetki davranışını (validation, 401/403/404, 422, 201, 429) yakalar → `src/test/resources/laravel/identity.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_app_release.py` | Faz 3: Laravel'in sürüm klasörüne senaryo dosyaları yazıp `/api/app/*` cevaplarını (başlıklar ve APK baytları dahil) yakalar → `src/test/resources/laravel/app-release.json`. Rate limit ortak 30/dk: önceki 60 sn içinde bu uçlara istek atılmamış olmalı | Evet |
| `capture_catalog.py` | Faz 4: katalogun yazma davranışını (77 durum) yakalar → `src/test/resources/laravel/catalog.json`. Laravel'de API ile yapılamayan hazırlıklar `sql` adımı olarak (Java testinde de çalışır). Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `laravel_capture.py` | Yakalama betiklerinin ortak parçaları (`Capture`: case, sql, yer tutucular, fixture yazma). Yeni modüllerin `capture_*.py`'leri bunu kullanır | Evet |
| `capture_inventory.py` | Faz 5a: tank yazma davranışı (41 durum) → `src/test/resources/laravel/storage-unit.json` | Evet |
| `capture_customer.py` | Faz 9a: müşteriler (70 durum; müşteri ve adres CRUD, varsayılan adres, formül tercihleri ve profili, Laravel'in 500'leri) → `src/test/resources/laravel/customer.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_finished_product.py` | Faz 8a: tarifler ve bitmiş ürünler (74 durum; versiyonlama, oran toplamı, depo dökümü, silme korumaları, Laravel'in 500'leri) → `src/test/resources/laravel/finished-product.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_finished_product_storage.py` | Faz 8c: bitmiş ürün depoları (86 durum; raf kuralları, yerleştirme, transfer, çıkış, silme, Laravel'in 500'leri) → `src/test/resources/laravel/finished-product-storage.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_finished_product_run.py` | Faz 8b: dolum koşuları (91 durum; planlama kuralları, ambalaj tahsisi, durum geçişleri, tamamlamada tüketim, değişen tank/lot) → `src/test/resources/laravel/finished-product-run.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_material.py` | Faz 5b: materyal listesi, detay ve soy grafı cevapları (30 durum, veri değiştirmez) → `src/test/resources/laravel/material.json` | Evet |
| `capture_movement.py` | Faz 5c: stok hareketleri (56 durum) → `src/test/resources/laravel/storage-movement.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_notification.py` | Faz 10: bildirimler (38 durum; dürtme, bekleme süresi, liste, okunmamış sayısı, okundu işaretleme, dürtülecek kişiler) → `src/test/resources/laravel/notification.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_order.py` | Faz 9b: siparişler (91 durum; CRUD, durum geçişleri, stok kontrolü, FIFO ve elle raftan alma, sevkiyat, hazır sipariş iptali) → `src/test/resources/laravel/order.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_production_batch.py` | Faz 6a: üretim partileri (36 durum) → `src/test/resources/laravel/production-batch.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_analytics.py` | Faz 11a: analitik ve rapor oluşturucu (125 durum; aralık sınırları, bütün ölçü × gruplama birleşimleri, kayıtlı raporlar) → `src/test/resources/laravel/analytics.json`. Kayıtlı raporlar veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_daily_summary.py` | Faz 11b: günlük özet (17 durum; her rol, dolu günler, onay bekleyen aşamalar) → `src/test/resources/laravel/daily-summary.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_batch_process.py` | Faz 6b: prosesler (108 durum; bir partinin 12 aşaması, dört kullanıcı) → `src/test/resources/laravel/batch-process.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_packaging.py` | Faz 7: ambalaj (113 durum; ürün tedarikçileri, kalemler, lotlar, mal girişi, sayım düzeltmesi, silme; dört kullanıcı) → `src/test/resources/laravel/packaging.json`. Veri değiştirir: öncesinde ve sonrasında `restore.sh` | Evet |
| `capture_supplier.py` | Faz 4: tedarikçi ve tarla yazma davranışı (28 durum) → `src/test/resources/laravel/supplier.json` | Evet |
| `compare.py` | Aynı isteği Laravel'e ve Java'ya aynı anda atıp JSON cevapları karşılaştırır | Evet |
| `responses/` | Yakalanan cevaplar (84 istek, ~21 MB) | Hayır, yeniden üretilir |

Endpoint envanteri: [docs/laravel-reference/ENDPOINTS.md](../docs/laravel-reference/ENDPOINTS.md)

## Kullanım

```bash
# Laravel ayakta olmalı (Olive_Production_Tracking_App_Back/olive_oil_back içinde: docker compose up -d)
LARAVEL=../Olive_Production_Tracking_App_Back/olive_oil_back

reference/restore.sh "$LARAVEL"                       # veriyi dondurulmuş hale döndür
python -I reference/capture_responses.py "$LARAVEL"   # cevapları yakala
```

`seed-data.sql` yeniden üretmek için (referans veri değiştiğinde): `reference-data.sql`'i boş bir Postgres 17'ye yükleyip
Flyway ile V2 ve sonrasını uygulayın (baseline 1), sonra `pg_dump --data-only --no-owner --no-privileges` alın.
`materials.parent_material_id` kendi tablosuna bakan bir foreign key; pg_dump bunun için uyarı verir. Mevcut dökümde hiçbir
kayıt kendinden sonraki bir id'yi göstermediği için yükleme FK kontrolleri açıkken sorunsuz geçer (`BaselineSchemaIT` doğrular).

`restore.sh` hedef veritabanındaki her şeyi siler. Sadece `COMPOSE_PROJECT_NAME` içinde `dev` geçen ortamlarda çalışır.
Java'nın kendi tabloları (`flyway_schema_history`, `event_publication`) da silinir; Java bir sonraki açılışta yeniden oluşturur.

## Laravel / Java karşılaştırması (`compare.py`)

Aynı isteği iki sisteme **aynı anda** atar ve JSON cevapları karşılaştırır. İki sistem aynı
veritabanına baktığı için zamana bağlı cevaplar da karşılaştırılabilir. Token Laravel'den alınır.

```bash
# Bir modülün bütün GET endpoint'leri (routes.json'dan, parametreler veritabanından seçilir)
python -I reference/compare.py "$LARAVEL" --only api/app

# Belirli istekler
MSYS_NO_PATHCONV=1 python -I reference/compare.py "$LARAVEL" --path "/api/customers?per_page=2&page=3"
```

- Git Bash'te `/api/...` ile başlayan argümanlar Windows yoluna çevrilir; `--path` kullanırken başa `MSYS_NO_PATHCONV=1` yazın.
- Sayfalama linklerindeki adresler (`localhost:8001` / `localhost:8081`) `{BASE}` ile değiştirilerek karşılaştırılır.
- Sırası rastgele olan diziler (rol izinleri) varsayılan olarak sırasız karşılaştırılır; başka yollar için `--unordered "data.*.alan"`.
- Çıkış kodu: hepsi aynıysa 0. Farklar JSON yoluyla birlikte yazılır.
- Araç doğrulaması: Laravel kendisiyle (doğrudan `8001`, proxy üzerinden `8080`) karşılaştırıldığında 84/84 aynı.

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

### Hata ve validation formatları (Faz 1'de yakalandı)
- 401: `{"message": "Unauthenticated."}`. `Accept: application/json` yoksa Laravel 500 HTML dönüyor (bkz. DEVIATIONS.md)
- 403: `{"message": "User does not have the right roles."}` / `"... right permissions."` (Spatie)
- 404 model: `{"message": "No query results for model [App\\Models\\Order] 999999"}`
- 404 route: `{"message": "The route api/x could not be found."}`
- 405: `{"message": "The PATCH method is not supported for route api/auth/login. Supported methods: POST."}`
- 422: `{"message": "<ilk hata> (and N more error[s])", "errors": {"alan": ["..."]}}`. Mesajlar İngilizce (Laravel projesinde `lang` klasörü yok)
- Yanlış şifre: 401 değil **422** `{"message": "The provided credentials are incorrect.", "errors": {"email": [...]}}`
- `per_page=` (boş) iki hata verir (integer + min:1); `05` geçersiz, `+5` geçerli; geçersiz `page` sessizce 1 olur
- Linklerde boş parametreler yazılmaz, değerler kırpılmış haliyle yazılır, tekrarlanan parametrede son değer ilk geçtiği sırada durur

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
