# PHP → Java Geçiş Yol Haritası

Kaynak: `Olive_Production_Tracking_App_Back/olive_oil_back`, `dev` branch'i, `ba61990` commit'i itibarıyla (Laravel 12, Sanctum, Spatie Permission, PostgreSQL 17)
Hedef: bu repo (Spring Boot 4, Java 25, Spring Modulith). Teknolojiler için bkz. [TECH_STACK.md](TECH_STACK.md)

## Mevcut sistemin boyutu
- 57 model, 69 migration, ~600 satır route (`routes/api.php`)
- 38 servis (~10.000 satır iş mantığı). En büyükleri: StorageMovement, BatchProcess, FinishedProductStorage, Analytics, FinishedProductRun, OrderService
- 29 feature test (Pest)
- `docs/` altında her akış için workflow dokümanı (`project-overview.md` güncel değil; DB MySQL değil, Postgres)
- Veritabanı zaten PostgreSQL. Veritabanı motoru değişmiyor.

## Başlangıç durumu
- Geliştirme yeni bir bilgisayarda yapılıyor. Bu bilgisayarda Laravel'in veritabanı ya da verisi **yok**.
- Kurulu: Docker. Kurulu değil: PHP, Composer, Java, psql.
- PHP ve Composer kurulmayacak, Laravel Docker konteynerinde çalışacak. psql için de Postgres konteyneri kullanılacak.
- Canlı veri sunucuda (droplet) duruyor. Geliştirme boyunca ona dokunulmaz, sadece canlıya geçişte kullanılır.

---

## Nihai hedef: tek başına Java

Laravel ve Java kalıcı olarak birlikte yaşamayacak. Hedef Laravel'in tamamen kalkması ve
Java reposunun tek başına yeterli olması: yeni kurulmuş bir bilgisayarda repo klonlanır,
PostgreSQL kurulur, boş veritabanı `--seed` ile doldurulur ve uygulama çalışır.

Bu yüzden **Java her an Laravel olmadan da açılabilir, seed'lenebilir ve testlerini geçebilir.**
Aşağıdaki birlikte çalışma düzeni (ortak veritabanı, proxy) sadece geçiş sırasında karşılaştırma
ve kademeli canlıya alma içindir; `laravel` profili ve `compose.laravel.yaml` dosyasında durur,
Faz 12'de silinir. Java tek başına her an çalışır ama **tam işlevli** olması geçişin sonunu
bekler: taşınmamış modüllerin endpoint'leri o zamana kadar Laravel'dedir.

## Strateji: Parça parça geçiş (Strangler Fig)

Tek seferde geçiş yapılmaz. Her modül şu sırayla taşınır:

1. **Laravel ve Java aynı veritabanını kullanır.** Veri taşıma gerekmez, iki sistem aynı anda çalışabilir.
2. **Önlerinde bir reverse proxy (Nginx/Caddy) durur.** Taşınan modülün endpoint'leri Java'ya, geri kalanı Laravel'e yönlenir.
3. **Frontend hiçbir değişiklik fark etmez.** Java, Laravel'in API sözleşmesini birebir korur.
4. **Bir modül Java'da tamamlanınca proxy'de o modülün yolu Java'ya çevrilir.** Sorun çıkarsa tek satırla geri alınır.
5. **Son modül de taşınınca Laravel kapatılır.**

### Hangi iş ne zaman yapılır
- **Ortak olan şeyi değiştiren her iş Geçiş Sonrası'na kalır.** Geçiş boyunca şema, API sözleşmesi ve auth Laravel ile Java arasında ortaktır. Bunlardan birini değiştirmek iki sistemi birden etkiler.
- **Sadece Java tarafını etkileyen, ucuz olan ve geçişi kontrol etmeye yardım eden işler baştan yapılır.** Gözlemlenebilirlik ve test altyapısı bu gruptadır.

### Her modül için tamamlanma kriteri
- [ ] Laravel'deki ilgili feature testleri Java'ya integration test (`*IT`) olarak taşındı ve geçiyor
- [ ] Aynı isteğe Laravel ve Java'nın verdiği JSON cevaplar karşılaştırıldı ve aynı (alan adları, null'lar, tarih formatı, pagination)
- [ ] Yetki kontrolleri aynı: hangi izin hangi endpoint'i açıyor
- [ ] Hata cevapları aynı: 422 validation, 404, iş kuralı hataları
- [ ] Proxy'de yönlendirme yapıldı ve dev ortamında frontend ile elle denendi
- [ ] Grafana'da o modülün hata oranı ve yanıt süreleri normal
- [ ] İlgili `docs/workflow-*.md` dokümanına "Java'da şurada" notu eklendi
- [ ] **Laravel olmadan:** Java tek başına açılıyor, `--seed` ile boş veritabanı kuruluyor, `./mvnw verify` geçiyor
- [ ] Ancak bundan sonra bir sonraki modüle geçilir

---

# Bölüm A: Kod Geçişi

## Faz 0: Yerel Ortam, Kararlar ve Hazırlık

### 0.1 Bu bilgisayarın kurulumu
- [x] Docker Desktop'ın çalıştığı doğrulanır (`docker info`)
- [x] JDK 25 kurulur
- [x] Laravel reposunun `dev` branch'inde olduğu ve güncel olduğu doğrulanır

### 0.2 Laravel'i yerelde ayağa kaldırmak (referans sistem)
- [x] `olive_oil_back/.env`, `.env.example`'dan oluşturulur. `COMPOSE_PROJECT_NAME` içinde `dev` geçmeli, yoksa `seed-demo.sh` çalışmaz
- [x] `docker compose up` ile Laravel ve Postgres başlatılır
- [x] Migration'lar çalıştırılır (69 migration, boş DB'ye)
- [x] `seed-demo.sh` ile demo fabrika verisi yüklenir (zeytinden siparişe tüm akış)
- [x] Laravel feature testlerinin konteynerde geçtiği doğrulanır. Başlangıçta kırık test varsa not edilir, Java tarafına "kırık" olarak taşınmaz

### 0.3 Referans veriyi dondurmak
- [x] Seed'den sonra veritabanının tam bir `pg_dump`'ı alınır (`reference-data.sql`)
- [x] Demo seeder'lar Faker ile rastgele veri üretebileceği için karşılaştırma testlerinden önce **her seferinde bu dump geri yüklenir**. Böylece Laravel ve Java hep aynı veri üzerinde karşılaştırılır

### 0.4 Kararlar ve envanter
- [x] **API sözleşmesi:** Laravel formatı birebir korunacak (snake_case JSON, `data / links / meta` pagination, `{message, errors}` validation hataları)
- [x] **Laravel API'sinin envanteri:** tüm endpoint'ler (`php artisan route:list --json`, konteynerde), her birinin izni ve kullanılan Resource sınıfı
- [x] **Modül sınırları:** aşağıdaki faz listesi Modulith paketlerine karşılık gelir
- [x] **Referans cevaplar:** dondurulmuş veri üzerinde her GET endpoint'inin Laravel cevabı kaydedilir. Veri değiştiren endpoint'lerin referansları her modülün kendi fazında, veri geri yüklenerek alınır

> Faz 0 çıktıları ve bulgular: [reference/README.md](../reference/README.md), endpoint envanteri (191 endpoint): [laravel-reference/ENDPOINTS.md](laravel-reference/ENDPOINTS.md)

## Faz 1: İskelet ve Ortak Altyapı

Hiçbir iş mantığı içermez, sonraki her fazın üzerine kurulacağı temel.
- [x] Spring Boot 4 projesi, Maven wrapper, Modulith iskeleti (Spring Boot 4.1.2, Modulith 2.1.1, paket `com.olivamore.api`)
- [x] **Tek başına çalışma:** `compose.yaml` (isteğe bağlı Docker Postgres, Mailpit, Prometheus, Grafana) Laravel'e bağımlı değil. Veritabanı bilgisayardaki PostgreSQL ya da Docker'daki (`COMPOSE_PROFILES=docker-db`). `--seed`: boş veritabanına Flyway şemayı kurar, demo veri (`reference/seed-data.sql`) JDBC ile yüklenir, psql gerekmez. Yeni bilgisayar senaryosu temiz klonla denendi
- [x] **Geçiş dönemi düzeni** (`compose.laravel.yaml`, `laravel` profili): reverse proxy (Caddy, `:8080`), Laravel'in veritabanına köprü (`127.0.0.1:5433`). Laravel'in compose'una dokunulmadı: köprü ve proxy onun Docker ağına katılıyor. Taşınan yollar `docker/proxy/java-routes.caddy`'ye eklenir, cevaplarda `X-Served-By` başlığı var
- [x] **Flyway baseline:** Her migration ayrı bir dosya, Laravel'deki gibi tablo tablo: `db/migration/V0.01__create_users_table.sql` ... `V0.69__add_order_to_finished_product_stock_movements.sql` (+ `V0.00__create_migrations_table.sql`). SQL Laravel'in kendisine üretildi (`migrate --pretend`), yalnızca şema komutları alındı. Mevcut Laravel veritabanında şema sürüm 1 olarak kabul edilir (baseline), V0.xx'ler çalışmaz; boş veritabanında hepsi sırayla çalışır. (İlk hâli tek dosyaydı, `V1__baseline.sql`; okunabilirlik için bölündü.) `BaselineSchemaIT` Flyway + seed ile kurulan veritabanının Laravel'inkiyle şema (kolon, constraint, index), satır sayıları ve sequence'ler düzeyinde aynı olduğunu doğrular. Geçiş bitene kadar Laravel tablolarındaki değişiklikler **sadece Laravel migration'larıyla** yapılır; Java sadece kendi altyapı tablolarını ekleyebilir (`V2`: Modulith `event_publication`)
- [x] **Jackson:** snake_case; `Instant`/`LocalDateTime` → `2026-11-06T15:35:14.000000Z` (UTC, mikrosaniye); gelen metinler Laravel gibi kırpılır, boş metin null olur (`TrimStrings` + `ConvertEmptyStringsToNull`, şifre alanları hariç). Query parametreleri için aynısı `InputNormalizationFilter`'da
- [x] **Ortak pagination** (`Listing`, `LaravelPaginator`): `per_page` yoksa sayfasız `{"data": [...]}`, varsa Laravel'in `data / links / meta` formatı. "..." penceresi, link URL'leri, `per_page` validation'ı ve geçersiz `page` davranışı Laravel'den yakalanan 11 fixture ile birebir test edildi
- [x] **Global hata yönetimi** (`ApiExceptionHandler`): 422 validation (`(and N more errors)` özeti, alan sırası, tip hataları), `BusinessRuleException` (boş `errors` → `{}`), 404 model (`No query results for model [App\Models\Order] 1`), 404 route, 405, 500. Gövdeler Laravel'den yakalanan cevaplarla test edildi. **403 ve 401 Faz 2'de** (Spring Security ile)
- [x] **Silme koruması** (`DeletionGuard`): `"X silinemez; kullanıldığı yerler: 3 ürün, 2 parti."`
- [x] **Test altyapısı:** Testcontainers Postgres 17, `--seed` ile aynı kurulum (boş veritabanı + Flyway + demo veri). `reference/compare.py`: aynı isteği Laravel'e ve Java'ya aynı anda atıp JSON'u karşılaştırır (adres normalizasyonu, sırasız diziler). Laravel'i kendisiyle (doğrudan ve proxy üzerinden) karşılaştırarak doğrulandı: 84/84 aynı. `OliveApiApplicationIT` gerçek Tomcat'te çalışır
- [x] **Gözlemlenebilirlik (en basit haliyle):** Actuator, Micrometer, Prometheus, Grafana ("Olive API" dashboard'u: istek/sn, 5xx oranı, p95, 4xx, DB havuzu, heap; Modulith insight sayesinde metriklerde modül adı da var). Her modül proxy'de Java'ya çevrildikten sonra hata oranı ve yanıt süresini izlemek için. Detaylı dashboard'lar Geçiş Sonrası'nda

## Faz 2: Kimlik ve Yetki (identity)

Diğer bütün modüller buna bağlı olduğu için ilk taşınacak modül budur.
- [x] `users`, Spatie tabloları (`roles`, `permissions`, `model_has_roles`, `model_has_permissions`, `role_has_permissions`) ve `personal_access_tokens`: entity'ler JPA, morph kolonlu pivot tablolar SQL ile (`Assignments`)
- [x] **Sanctum uyumlu auth:** Java, Laravel'in token'larını doğrular (`id|token` ya da yalnız token, SHA-256, sabit süreli karşılaştırma, `last_used_at` güncellemesi). Laravel'in ürettiği bir token Java'da, Java'nınki Laravel'de geçerli; birinde çıkış yapılan token diğerinde de geçersiz (canlı denendi)
- [x] **Java token'ı kendisi de üretir:** Laravel'le aynı format (`<id>|<40 harf/rakam><8 hex crc32>`); Laravel'in ürettiği gerçek bir token'la doğrulandı
- [x] **Token doğrulaması tek noktada:** `TokenService.authenticate()`. Geçiş Sonrası'nda (B2) JWT buraya konulur
- [x] **Yetki:** Spring Security authority'leri yerine kendi anotasyonlarımız: `@Public`, `@RequiresRole`, `@RequiresPermission` (`identity` modülünün API'si). `/api/**` varsayılan olarak giriş ister. Laravel'deki sıra korunur: 401 (interceptor) → 404 (model binding, `@Bound`) → 403 (controller metodunun hemen önünde, AOP). Admin her izne sahip (`Gate::before`), 403 mesajları Spatie'ninkiyle aynı
- [x] Login/register throttle: iki katmanlı login limiti (hesap+adres 5/dk, adres 20/dk, e-posta küçük harfe çevrilip kırpılır), register 5/dk; `X-RateLimit-*`, `Retry-After` başlıkları Laravel'le aynı
- [x] User, Role, Permission, UserRole endpoint'leri ve `/api/auth/*`, `/api/user` (17 endpoint)
- [x] **Ortak altyapıya eklenenler** (sonraki fazların hepsi kullanacak): `LaravelValidator` (FormRequest kuralları aynı metinle, Laravel'in Validator mantığı birebir), `RequestInput` (`$request->all()`), `@Bound` model binding, `@Throttle` rate limiting, `HttpStatusException` (`abort()`), `DataResponse` (201 dahil), `Timestamps` (UTC, saniye)
- [x] **Laravel'de bulunan hatalar** Java'da düzeltildi: izinli rol oluşturma ve rol silme 500 (yanlış guard), login'e dizi 500, admin'i `roles: null` ile güncelleme 500 (bkz. DEVIATIONS.md)
- [x] Proxy: identity yolları Java'ya yönlendirildi (`docker/proxy/java-routes.caddy`), geri kalanı Laravel'de
- Testler:
  - `IdentityParityIT`: Laravel'den yakalanan 53 durum (`reference/capture_identity.py`) aynı sırayla Java'ya tekrar oynatılır, status + gövde + rate limit başlıkları karşılaştırılır: 51 aynı, Laravel hatası olan 3 durum düzeltilmiş haliyle, 2 durum `/api/customers`'ı beklediği için Faz 9'a kadar atlanıyor
  - `IdentityAccessIT`: `AuthThrottleTest`'in 6 testi, `PermissionTest`'in identity kısmı (admin bütün izinler, fabrika hesapları, izinsiz kullanıcı), Laravel/Java token uyumluluğu
  - `LaravelValidatorTest`, `TokenServiceTest`
  - Canlı karşılaştırma (`compare.py`): 7 GET endpoint'i 7/7 aynı

## Faz 3: Bağımsız Küçük Uç (app-release)

Veritabanına dokunmayan, giriş gerektirmeyen iki endpoint: mobil uygulamanın kendi sunucumuzdan güncellenmesi.
- [x] `/api/app/version`, `/api/app/download` (`apprelease` modülü). Sürüm klasörü `olive.releases-path` (`APP_RELEASES_PATH`, varsayılan `releases/`; `laravel` profilinde Laravel'in `docker-data/app` klasörü)
- [x] Manifest (`version.json`) Laravel'deki gibi okunur: PHP `(int)` / `(string)` dönüşümleri (`"7abc"` → 7, `true` → `"1"`), `isset` kontrolü, BOM'lu ya da bozuk JSON geçersiz; `notes`, `sha256`, `published_at` olduğu gibi
- [x] İndirme Symfony `BinaryFileResponse` gibi: `Content-Disposition` (boşlukta tırnak, ASCII dışı adda `Str::ascii` yedek + `filename*`), Range (206), HEAD, `Cache-Control: public`, `Last-Modified`; klasör dışına çıkış `basename` ile engellenir
- [x] `throttle:30,1`: iki endpoint istemci başına dakikada toplam 30 isteği paylaşır (Laravel'de anahtar route'a değil istemciye bağlı; `@Throttle` buna göre düzeltildi)
- [x] Ortak altyapıya eklenenler: `Php` (intval, strval, basename, ascii), her cevaba `Cache-Control: no-cache, private` (Laravel/Symfony varsayılanı)
- [x] Proxy: `/api/app/*` Java'da; `download_url` istemcinin gördüğü adresle (proxy) üretiliyor
- Testler:
  - `AppReleaseParityIT`: Laravel'den yakalanan 32 istek (`reference/capture_app_release.py`, 17 senaryo: sürüm yok, PHP dönüşümleri, bozuk/BOM'lu manifest, klasör dışı, Türkçe dosya adı, Range, HEAD, ortak sınır) aynı dosyalarla tekrar oynatılır: 32/32 aynı. Laravel'in `AppReleaseTest`'indeki 4 durum bunların içinde
  - `AppReleaseControllerTest`, `PhpTest`
  - Canlı karşılaştırma (`compare.py`): 2/2 aynı; Range ve Türkçe dosya adı elle de karşılaştırıldı

## Faz 4: Üretim Altyapısı ve Ana Veriler (catalog)

Henüz hareket yok, sadece tanımlar var. Sonraki her modül buna bağlı. Resource'lar birbirine sıkı bağlı
olduğu için (ürün → hat → aşama → proses tipi → alan) iki parçada yapıldı: katalog ve tedarikçiler.

**Katalog (tamamlandı)**
- [x] Production product (kategori), production product type, attribute definitions ve kategori eşlemeleri. Dinamik alan tabloları mevcut yapısıyla birebir (JSONB Geçiş Sonrası'nda)
- [x] Process type, process type field, stage extension type ve alanları
- [x] Production line (girdi/çıktı ürünleri), production stage (sıra kaydırma birebir: `(hat, sıra)` unique olduğu için geçici 100000 kaydırma), stage extension senkronizasyonu (değerler, girdi/çıktı ürünleri)
- [x] **JPA yerine `Db` (JDBC):** Eloquent davranışı birebir: değerler PDO gibi tipsiz, booleanlar `t`/`f`; `create()` yalnızca verilen kolonları yazar ve cevabı veritabanından değil verilen değerlerden kurar (DB varsayılanları görünmez); `update()` yalnızca değişeni yazar; ilişkiler Laravel'in `where x in (1, 2, 3)` sorgularıyla aynı biçimde ve aynı `ORDER BY` ile (sıralanmayan / eşit sıralı satırlar Laravel'deki sırada)
- [x] Resource'lar `whenLoaded` davranışıyla: hangi ilişki yüklendiyse o anahtar var (örn. ürün üzerinden gelen hatlarda `extensions` yok); `decimal:4`, `boolean`, `integer`, `array` cast'leri
- [x] Validator'a eklenenler: `distinct`, `required_with`, `unique`'te `where` koşulları; `*` kuralından genişleyen alanların mesajdaki adı Laravel'deki gibi ham (`items.0.product_id`)
- [x] Laravel'de bulunan hatalar Java'da düzeltildi: attribute eşlemesi ekleme (her zaman 500), eşleme güncellemede `attribute_key` (500), proses alanında tekrar eden anahtar (500), proses tipini kendi adıyla güncelleme (422). Bkz. DEVIATIONS.md
- [x] Proxy: katalog yolları Java'da
- Testler:
  - `CatalogParityIT`: Laravel'den yakalanan 77 durum (`reference/capture_catalog.py`; ekleme/güncelleme/silme, silme korumaları, aşama sırası, uzantı senkronizasyonu, validation) tekrar oynatılır: 73 aynı, Laravel hatası olan 4 durum düzeltilmiş haliyle
  - Canlı karşılaştırma (`compare.py`): veritabanındaki **bütün** katalog kayıtları (her ürün, tip, hat, aşama, proses tipi, alan ve hata durumları) 120/120 aynı

**Tedarikçiler (tamamlandı)**
- [x] Supplier (zeytin tedarikçisi) ve supplier field (tarla): liste (`is_active` filtresi PHP `filter_var` ile, boş değer de filtreler; arama ad/kod/tarla kodunda `ilike`), ekleme (tarlalarla birlikte), güncelleme (yalnızca PATCH), silme korumaları
- [x] `GET /api/suppliers/{id}/deliveries`: tedarikçinin partileri ve kantar tartımları. Üretim tablolarını (Faz 6) yalnızca okuyor; Faz 6'da üretim modülünün okuma API'sine dönüşebilir
- [x] **Product supplier (ambalaj tedarikçisi) Faz 7'ye taşındı:** cevapları ambalaj lotlarını (`PackagingLotResource`) iç içe döndürüyor
- [x] Proxy: `/api/suppliers` Java'da
- Testler:
  - `SupplierParityIT`: Laravel'den yakalanan 28 durum (`reference/capture_supplier.py`; Laravel'in `SupplierTest`'indeki ekleme, kod tekrarı, tarla sahipliği, silme koruması dahil): 28/28 aynı
  - Canlı karşılaştırma: her tedarikçi, teslimatları, `is_active` ve arama varyasyonları 67/67 aynı
  - Ortak test yardımcısı `LaravelReplay`: fixture'ı tekrar oynatma, yer tutucular, SQL adımları, Laravel hatası düzeltmeleri, sequence uyumu (sonraki fazlar da kullanır)

## Faz 5: Depo ve Malzeme (inventory) (tamamlandı)

**En kritik faz.** Stok defteri buradan yönetiliyor, en büyük servis (StorageMovement) de burada.
Üç adımda: 5a tanklar, 5b malzemeler, 5c stok hareketleri.

**5a: Tanklar (tamamlandı)**
- [x] Storage unit CRUD: kapasite metrikleri (doluluk yalnızca kapasite birimindeki bakiyelerden, PHP float + `round(…, 2)`), silme korumaları (aktif malzeme, hareket kaydı), 204 silme
- [x] İzinli ürün ve tip senkronu, attribute kuralları (yalnızca decimal attribute, min ≤ max; sonraki kuralda hata olursa hepsi geri alınır)
- [x] Bakiyeler, hareket listesi (`per_page` ile sayfalı); `MaterialResource`, `StorageMovementResource` (5b ve 5c de kullanacak)
- [x] Validator'a `gt:N` eklendi; `Php.round` (PHP 8.4 yuvarlaması)
- [x] Proxy: `/api/storage-units` Java'da (`/movement-history` hariç, o 5c'de)
- Testler:
  - `StorageUnitParityIT`: Laravel'den yakalanan 41 durum (`reference/capture_inventory.py`): 41/41 aynı. Sıralamasız ilişkide (attribute kuralları) bir durumda liste id'ye göre karşılaştırılıyor (DEVIATIONS 11)
  - Canlı karşılaştırma: bütün tank GET'leri (her tankın detayı, bakiyeleri, hareketleri, sayfalı hareketler, hata durumları) 63/63 aynı

**5b: Malzemeler (tamamlandı)**
- [x] Liste: durum (`active` varsayılan, boşsa da), soy süzgeci (`root`, `root_raw`, `root_opening`, `derived`), arama (materyal no `#12`, not, ürün tipi, parti kodu, tank adı/kodu; `ilike`), en yeni 200
- [x] Detay (`MaterialDetailResource`): miktar dökümü (giren/ayrılan/kapanan/kalan, PHP float), ebeveyn ve çocuklar, attribute değerleri (tanım sırasıyla), son 20 hareket, bu malzemeden çekilen dolum koşuları
- [x] Soy grafı (`MaterialLineageService`): köke çıkış, kökten genişlik öncelikli iniş (400 düğüm sınırı), dolum koşusu, depo yerleştirmesi ve (tek koşudan beslenen depolarda) sipariş düğümleri, en uzun yol derinliği; alt yazı `number_format` ile ("1.234 L", "12,5 kg"). Bitmiş ürün ve sipariş tablolarını (Faz 8, 9) yalnızca okuyor
- [x] Proxy: `/api/materials` Java'da
- Testler:
  - `MaterialParityIT`: Laravel'den yakalanan 30 cevap (`reference/capture_material.py`): 30/30 aynı
  - Canlı karşılaştırma: **bütün** malzemelerin (2091) detayı ve soy grafı 4182/4182, liste varyasyonları 24/24 aynı
  - `LaravelReplay` fixture yollarını yeniden kodlamıyor (Türkçe arama `%C3...` çift kodlanıyordu)

**5c: Stok hareketleri (tamamlandı)**
- [x] Açılış, giriş (var olan materyal: parça ya da tamamı; yeni materyal: partiden, tipin biriminde), çıkış (consumed), transfer (iki hareket, `TRF-…` referansı), düzeltme (artış yeni materyal, azalış discarded), tankın hareket geçmişi (tarih aralığı, `per_page`)
- [x] Tank kuralları Laravel'deki sırayla: miktar, birim, izinli ürün/tip, kapasite, farklı ürün/tip, attribute kuralları (gelen değer ve ağırlıklı ortalama); materyal bölme (epsilon 0.00005, attribute değerleri kopyalanır), bakiye yeniden hesabı (`decimal:2` değişmediyse dokunulmaz)
- [x] PHP float: hesaplar double, veritabanına 14 basamak (`Php.floatString`)
- [x] Validator'a `required_without`, `prohibits`, `different` eklendi; `Rule::requiredIf` (`$request->boolean`) Java'da kural metnine çevriliyor
- [x] **Eşzamanlılık:** işlemdeki tanklar `SELECT ... FOR UPDATE` ile kilitleniyor (Laravel'de yok, DEVIATIONS 13)
- [x] Proxy: `/api/storage-movements` ve `/movement-history` Java'da
- Testler:
  - `StorageMovementParityIT`: Laravel'den yakalanan 56 durum (`reference/capture_movement.py`): 56/56 aynı (transfer referansı maskeli)
  - `StorageMovementConcurrencyIT`: 4 eşzamanlı giriş, tank taşmıyor
  - Canlı karşılaştırma: 15 tankın hareket geçmişi (sayfalı, tarihli, hata durumları) 63/63 aynı

## Faz 6: Üretim Partileri (production) (tamamlandı)

Faz 4 ve 5'e bağlı: prosesler malzeme üretir ve depoya hareket yazar. İki adımda: 6a partiler, 6b prosesler.

**6a: Üretim partileri (tamamlandı)**
- [x] Liste (varış günü süzgeci, `per_page`), takvim (gün → parti sayısı), detay; `ProductionBatchService::relations()` ağacının tamamı (hat, giriş/çıkış tipleri, prosesler, alan değerleri, aşama uzantıları, malzemeler)
- [x] Ekleme: hattın kategori/çeşit kuralları, ortak "type", çıktı tiplerinin çözülmesi, hattın her aşaması için bekleyen proses; cevap 200 (Laravel `fresh()`)
- [x] Güncelleme (tedarikçi değişip tarla verilmezse tarla düşer), silme koruması (başlamış proses, malzeme, stok hareketi)
- [x] Validator'a `date`, `date_format`, `after_or_equal` eklendi; `PhpDates` (Eloquent `datetime` cast'inin yazdığı değer: saat dilimi çevrilmez)
- [x] Modüller arası: `CatalogResources` (aşama uzantıları, proses alanları), `MaterialResources` (MaterialResource)
- [x] Proxy: `/api/production-batches` Java'da
- Testler:
  - `ProductionBatchParityIT`: Laravel'den yakalanan 36 durum (`reference/capture_production_batch.py`): 36/36 aynı
  - Canlı karşılaştırma: 273 partinin hepsi, tam liste (12 MB), sayfalı liste, takvim: 277/277 aynı. Tam liste Java'da 0,3 sn, Laravel'de 2,4 sn
  - `LaravelReplay` farkı JSON yoluyla gösteriyor (`data.processes.0.extensions.0.values: laravel=… java=…`)

**6b: Prosesler (tamamlandı)**
- [x] Proses detayı (`BatchProcessService::relations()`: onaylayan/gönderen, inceleme geçmişi, uzantı değerleri, materyaller tüm ilişkileriyle; `requires_approval` hesaplanmış)
- [x] Durum akışı: katı sıra, başlatma/bırakma, tamamlama, atlama (yalnızca zorunlu olmayan aşama), başarısızlık, geri açma (yan etkisi olan ya da sonraki aşaması başlamış aşama geri alınamaz); parti durumu proseslerden türetiliyor
- [x] Yetkiler: tamamlama, atlama, kapanmış aşamayı geri açma ayrı izinler (doğrulamadan önce 403); identity'ye `Permissions` (`$user->can()`)
- [x] Uzantı işleyicileri (`StageHandlers`): tartım, dönüşüm (kütle dengesi), analiz (attribute değerleri), depo hareketi (sabitlenmiş tür; inventory'ye `StockOperations`)
- [x] `operation_data` doğrulaması aşamanın uzantılarına göre; yalnızca doğrulanan alanlar saklanıyor (Laravel `validated()`)
- [x] Onay akışı: onay gereken aşamada (işaretli, depo hareketi yapan ya da son aşama) gönderim işleyicileri bir savepoint'te deneyip geri alıyor, onayda işliyor; red gerekçeli, inceleme kaydı
- [x] Alan değerleri: veri tipine göre doğrulama (`after` kancası) ve `value_*` kolonları; gönderilmeyenler silinir
- [x] Validator: `required_without_all`, bağımlı kuralların parametrelerinde `*` genişletme, `after` kancası
- [x] Aşama uzantı değerleri Faz 4'te taşınmıştı (`ExtensionSync`)
- [x] Proxy: `/api/batch-processes` Java'da
- Testler:
  - `BatchProcessParityIT`: Laravel'den yakalanan 108 durum (`reference/capture_batch_process.py`): yeni bir partinin 12 aşaması uçtan uca, dört kullanıcıyla: 108/108 aynı
  - `LaravelReplay`: başka kullanıcıyla istek (`as`), yakalama anına kaydırılmış saat (`capturedAt`; 6a'nın takvim durumu güne bağlıydı)
  - Canlı karşılaştırma: veritabanındaki 3276 prosesin hepsinin detayı (tamamlanmış, bekleyen; malzemeli, inceleme geçmişli) 3276/3276 aynı

## Faz 7: Ambalaj (packaging) (tamamlandı)

Yeni `packaging` modülü. Tek gerçek kaynak lotların kalanı; `packaging_items.current_stock` her değişiklikten sonra yeniden hesaplanan önbellek.

- [x] Ürün tedarikçisi (ambalaj tedarikçisi; Faz 4'ten taşındı): liste (ad/kod araması, `is_active`), detay (lotları kalemiyle, yeniden eskiye), ekleme/güncelleme, silme koruması (lotu olan silinmez)
- [x] Ambalaj kalemi: liste (`latest()`, lot sayıları), ekleme (`current_stock` verilirse `DEVIR-2026-0018` devir lotu), güncelleme (PUT/PATCH; `current_stock` doğrulanır ama yazılmaz), silme korumaları (ürün bileşeni, lot)
- [x] Lot: liste (kalem, tedarikçi, durum, arama: lot no, kalem adı, tedarikçi adı/kodu), kalemin lotları, detay (hareketler dolum koşusu ve ürünüyle, bu lottan çeken koşular)
- [x] Mal girişi (lot numarası kalem içinde tekil, giriş hareketi), künye düzeltmesi (miktar hariç), sayım düzeltmesi/fire/iade (lot kilitlenir, kalanın altına inilmez, fire ve iade yalnızca düşüş, sıfırda `depleted`), silme (yalnızca hiç kullanılmamış lot, hareketleriyle)
- [x] Validator: FormRequest `messages()` karşılığı `Rules.message("lot_code.unique", …)`
- [ ] Dolum koşusu tahsisi (FIFO), elle tahsis, tüketim: `PackagingLotService` içinde ama çağıranı bitmiş ürün, Faz 8'de
- [x] Proxy: `/api/product-suppliers`, `/api/packaging-items`, `/api/packaging-lots` Java'da
- Testler:
  - `PackagingParityIT`: Laravel'den yakalanan 113 durum (`reference/capture_packaging.py`), dört kullanıcıyla: 113/113 aynı
  - Canlı karşılaştırma: 337 lotun, 17 kalemin (detay ve lotları), 3 tedarikçinin hepsi, liste varyasyonları ve hata durumları 395/395 aynı

## Faz 8: Bitmiş Ürün (finished-product) (tamamlandı)

Faz 5, 6 ve 7'ye bağlı: dolum koşusu tanktan yağ, depodan ambalaj tüketiyor. Yeni `finishedproduct` modülü, üç adımda: 8a tarifler ve ürünler, 8b dolum koşuları, 8c bitmiş ürün depoları.

**8a: Tarifler ve bitmiş ürünler (tamamlandı)**
- [x] Tarif: aktif tarifler (`?name=`), detay, versiyonlar (Laravel'de izin grubunun dışında: yalnızca giriş), ekleme (içerik oranları toplamı 1, attribute kuralları)
- [x] Tarif güncellemesi yeni versiyon: eskisi pasif, verilmeyen içerik ve kurallar kopyalanır, ürünler yeni versiyona taşınır; cevap 201. Yalnızca aktif versiyon güncellenir. Silme korumaları (koşu, ürün)
- [x] Ürün: liste ve detay (tarif, ambalaj bileşenleri, raf konumları, rafta/sevkiyat bekleyen/açıkta adet), ekleme, güncelleme (fabrikada şişesi olan ürünün hacmi değişmez, bileşenler yenilenir), silme (dolum kaydı olan silinmez)
- [x] Laravel'in 500 verdiği yedi durum 422 (DEVIATIONS 14): sayı olmayan oran, aynı içerik/attribute/ambalaj kalemi iki kez (`distinct`), siparişi ya da stoğu olan ürünü silme
- [x] Proxy: `/api/finished-product-recipes`, `/api/finished-products` Java'da
- Testler:
  - `FinishedProductParityIT`: Laravel'den yakalanan 74 durum (`reference/capture_finished_product.py`): 74/74 aynı (7'si sapma, Java cevabı sabit)
  - Canlı karşılaştırma: bütün tarifler (detay, versiyonlar), 22 ürünün detayı, liste varyasyonları, hata durumları 48/48 aynı

**8b: Dolum koşuları (tamamlandı)**
- [x] Liste (`per_page` ile sayfalı), detay (tarif içeriği, tank, materyal payları ve yoğunluk, ambalaj tahsisi lot ve tedarikçiyle, attribute değerleri, izinli durum geçişleri)
- [x] Planlama: tarif ve tank kontrolleri (boş tank, tarifte olmayan ürün, blend oranı ±%2), şişe adedi × hacim ile tanktan çekilecek yağ (birimi tankın; KG tankta yoğunlukla çevrim), fire payı (%2), tahmini attribute değeri ve kabul aralığı, materyal payları; cevap 201 (adet istekteki haliyle)
- [x] Ambalaj: planlamada FIFO tahsis, elle tahsis (kalem başına tam adet), tamamlamada tahsis yeniden doğrulanıp lotlardan düşülüyor, lot hareketleri koşuya bağlı. `packaging` modülüne `PackagingAllocations`
- [x] Durum geçişleri (planlı → devam → tamamlandı, iptal); tamamlama önce doğrular (ambalaj stoğu, tahsis, tanktaki materyal hâlâ yerinde mi), sonra ürün stoğu, ambalaj, tanktan çıkış (`StockOperations.outboundForRun`: `MANUAL` kaynaklı, `finished_product_run_id` dolu)
- [x] Gerçek attribute değeri (başka koşunun değeri 404), silme (yalnızca planlı)
- [x] Proxy: `/api/finished-product-runs` Java'da
- Testler:
  - `FinishedProductRunParityIT`: Laravel'den yakalanan 91 durum (`reference/capture_finished_product_run.py`): 91/91 aynı. İki listede sıra garantisi yok, sırasız karşılaştırılıyor (DEVIATIONS 11)
  - HTTP üzerinden yazan parity testleri (`packaging`, `finishedproduct`) `@DirtiesContext`: alfabetik sırada diğer modüllerden önce çalışıyorlar, sonraki sınıf temiz veritabanıyla başlıyor
  - Canlı karşılaştırma: 389 koşunun hepsinin detayı, tam liste, sayfalı varyasyonlar, hata durumları 399/399 aynı
  - Seed'deki tanklar LITRE; KG tank ve yoğunluk çevrimi parity testinde yok

**8c: Bitmiş ürün depoları (tamamlandı)**
- [x] Depo: liste ve detay (raflar ürün kilidi ve içeriğiyle, depo × ürün stoğu), ekleme (201, ilişkisiz), güncelleme, silme (stoklu depo silinmez)
- [x] Raf: ekleme (kilitli ürünün hacmi rafınkiyle aynı olmalı), güncelleme (kapasite kullanımın altına inmez, dolu rafın hacmi değişmez, başka ürün varken tek ürüne kilitlenemez, `null` ile kilit kalkar), silme (dolu raf silinmez)
- [x] Depoya yerleştirme: tek raf ya da `placements` ile çok raf, tek işlemde; açıktaki şişe (fabrikada − depolarda − sevkiyat bekleyen) toplam üzerinden, raf deposu, pasif depo, hacim, kilit, kapasite kontrolleri; dolum koşusuna bağlanabiliyor
- [x] Transfer (raftan rafa, aynı ya da başka depo; raflar id sırasıyla kilitli; iki hareket aynı UUID referansla), çıkış (raftan ve `total_stock`'tan düşer), hareket geçmişi
- [x] Dört sayım birlikte: `total_stock`, depo × ürün, raf × ürün, raf toplamı
- [x] Laravel'in 500 verdiği iki durum 422 (DEVIATIONS 15): hareket geçmişi olan raf ya da depoyu silme
- [x] Siparişe toplama ve iade (`pickForOrder`, `returnOrderPicks`): Faz 9b'de, `ShelfOperations` ile
- [x] Proxy: `/api/finished-product-storages` Java'da
- Testler:
  - `FinishedProductStorageParityIT`: Laravel'den yakalanan 86 durum (`reference/capture_finished_product_storage.py`): 86/86 aynı (2'si sapma, Java cevabı sabit). Raflar, raf içeriği ve depo stoğu `ORDER BY`'siz; `LaravelReplay.unorderedEverywhere` bu anahtarlardaki listeleri her derinlikte id'ye göre karşılaştırıyor
  - Canlı karşılaştırma: 3 deponun detayı, hareket geçmişleri (2782 hareket), liste, hata durumları 9/9 aynı

## Faz 9: Müşteri ve Sipariş (sales) (tamamlandı)

Faz 8'e bağlı: sipariş hazırlanırken şişeler raftan düşüyor. Yeni `sales` modülü, iki adımda: 9a müşteriler, 9b siparişler.

**9a: Müşteriler (tamamlandı)**
- [x] Müşteri: liste (`is_active`, `per_page`), detay (adresleriyle), ekleme (adresleriyle birlikte; cevap yalnızca verilen alanlarla, 201), güncelleme, silme (siparişi olan silinmez)
- [x] Adres: liste, ekleme (varsayılan verilirse diğerleri varsayılan olmaktan çıkar; ülke verilmezse TR), güncelleme (başka müşterinin adresi 404), silme (siparişe bağlı adres silinmez; varsayılan silinirse en son eklenen varsayılan olur)
- [x] Formül tercihleri (ekleme, tekrar eklemede not güncellenir; çıkarma) ve profil: tercihler + iptal edilmemiş siparişlerden türetilen formüller (sipariş sayısı, toplam adet, son sipariş günü). Tarif resource'u `finishedproduct`'tan (`RecipeResources`)
- [x] Laravel'in 500 verdiği üç durum (DEVIATIONS 16): `NOT NULL` kolona gelen `null` (`is_active`, adreste `is_default`, `country`) alan yokmuş gibi
- [x] Proxy: `/api/customers` Java'da
- Testler:
  - `CustomerParityIT`: Laravel'den yakalanan 70 durum (`reference/capture_customer.py`): 70/70 aynı (3'ü sapma, yalnızca durum kodu)
  - Canlı karşılaştırma: 16 müşterinin detayı, adresleri, formül profili, liste varyasyonları, hata durumları 56/56 aynı

**9b: Siparişler (tamamlandı)**
- [x] Sipariş: liste (`status`, `customer_id`, sipariş günü ve oluşma zamanı, `per_page`), detay (müşteri, teslimat adresi, kalemler, oluşturan, durum geçmişi), taslak oluşturma (teslimat adresi müşterinin olmalı, aynı ürün iki kez olmaz; 201), güncelleme (yalnızca taslak/onaylı; kalemler yenilenir), silme (yalnızca taslak)
- [x] Durum geçişleri (taslak → onaylı → stok kontrol → hazır / üretimde → hazır → sevk; iptal), her geçiş geçmişe notuyla
- [x] Sipariş hazırlama: raftan düşüm durumla **aynı transaction'da ve senkron**; raf seçimi verilmezse FIFO (ürünün rafa ilk giriş zamanı), verilirse kalem başına tam adet. `total_stock` düşmez; sevkiyatta düşer. Hazır sipariş iptalinde şişeler alındıkları raflara geri konur (kapasite yeniden kontrol). `finishedproduct` modülüne `ShelfOperations`
- [x] Stok kontrolü: kalem başına fabrikada, depolarda, açıkta, diğer hazır siparişlere bağlı, kullanılabilir, eksik; raf konumları
- [x] Sevkiyat bekleyen adet defterden (`order_pick` + hazır sipariş), Faz 8a'da
- [x] Proxy: `/api/orders` Java'da
- Testler:
  - `OrderParityIT`: Laravel'den yakalanan 91 durum (`reference/capture_order.py`): 91/91 aynı
  - Canlı karşılaştırma: 335 siparişin detayı ve stok kontrolü, liste varyasyonları, hata durumları 679/679 aynı

## Faz 10: Bildirim (notification) (tamamlandı)

Yeni `notification` modülü. Laravel'de bildirim yalnızca uygulama içi ve elle "dürtme" ile üretiliyor; push ve mail yok, hiçbir modül olayla bildirim üretmiyor. Planlanan event listener'lar ve Spring Mail bu yüzden taşınmadı (Laravel'de karşılıkları olmadığı için); push eklendiğinde kanal kolonu (`in_app`) hazır.

- [x] Kullanıcının bildirimleri: liste (her zaman sayfalı, `per_page` varsayılan 30, en fazla 100; `?unread`; bağlantılarda query string yok, Laravel `paginate()` `withQueryString`'siz: `LaravelPaginator.paginateWithoutQueryString`), okunmamış sayısı, okundu işaretleme (başkasının bildirimi 422), hepsini okundu işaretleme
- [x] Dürtme (`notification.send`): beş iş türü (dolum koşusu, sipariş, bitmiş ürün, parti, proses), varsayılan olarak işin sorumlu rolündeki herkese (dürten hariç) ya da tek kişiye; kendini uyarma yok; aynı iş için aynı kişiye 3 dakika içinde ikinci uyarı yok; gövde kaydın okunur adı ve not (201)
- [x] Dürtülecek kişiler: sorumlu rollerdeki kullanıcılar, ada göre, rolleriyle
- [x] Proxy: `/api/notifications`, `/api/nudges` Java'da
- Testler:
  - `NotificationParityIT`: Laravel'den yakalanan 38 durum (`reference/capture_notification.py`): 38/38 aynı
  - Canlı karşılaştırma: 7 kullanıcının listesi (okunmamış, sayfalı), okunmamış sayısı, dürtülecek kişiler, hata durumu 42/42 aynı. Seed'de bildirim kaydı yok; dolu listeler parity testinde

## Faz 11: Analitik ve Günlük Özet (analytics) (tamamlandı)

Bütün modüllerin verisini okuduğu için en son taşınır. Hiçbir modül buna bağlı değil. Yeni `analytics` modülü. jOOQ eklenmedi: diğer modüller gibi `Db` ile Laravel'in Query Builder sorgularının aynısı çalışıyor (birebir uyum için en güvenli yol).

**11a: Analitik ve rapor oluşturucu (tamamlandı)**
- [x] Randıman (dönemlere göre, çeşide göre), tedarikçiler, müşteriler, tek müşteri (önceki dönemle); aşamalar adından değil görevinden (ilk zeytin tartımı, dönüşüm, yağ/pirina tartımı, yağ analizi)
- [x] Carbon 3 birebir: `diffInDays` ondalık (gün gün yalnızca ≤ 30 gün, hafta ≤ 119 gün; 731 gün farkı iki yılın sınırı), önceki dönem `subDays(gün + 0.99…)` yüzünden bir gün uzun, hafta pazartesi başlıyor (yerel ayar `tr`); PHP 8.4 `round`, float toplama sırası
- [x] Rapor oluşturucu: katalog, sorgu (10 ölçü × 6 gruplama, filtreler, tutarsız seçim 422), kayıtlı raporlar (kişiye özel; başkasının raporu 404)
- [x] Proxy: `/api/analytics/*` Java'da
- Testler:
  - `AnalyticsParityIT`: Laravel'den yakalanan 125 durum (`reference/capture_analytics.py`): 125/125 aynı

**11b: Günlük özet (tamamlandı)**
- [x] Açılış ekranı: üretim (giren, üretimde ve şu anki aşaması, biten), aşamalar (bugün kapanan, onay bekleyen), dolum koşuları, siparişler (bugün sevk edilen, açık, bugüne planlanan), stok (tükenen, depoya yerleşmemiş); her bölüm kullanıcının iznine göre
- [x] Proxy: `/api/daily-summary` Java'da
- Testler:
  - `DailySummaryParityIT`: Laravel'den yakalanan 17 durum (`reference/capture_daily_summary.py`): 17/17 aynı
  - Canlı karşılaştırma (11a + 11b): 26 aralık × randıman/tedarikçi/müşteri, 16 müşteri, rapor sorguları, 373 günün günlük özeti, beş rol: 674 istek; 659 birebir, 15'i yalnızca sıra farkı (bugün sevk edilen siparişler `order_date desc`, aynı günlü siparişlerde sıra tanımsız: DEVIATIONS 11)

## Yapı düzenlemesi (Faz 11 sonrası, tamamlandı)

Davranış değişmeden kod konu klasörlerine ayrıldı. Her klasörde Laravel adlarıyla `XxxController`,
`XxxService`, `XxxResource` ve `XxxRequests` bulunur; modüller arası erişim yalnızca modül kökündeki sınıflarla.
- [x] `catalog` dört modüle bölündü (bağımlılık yönü product → productionline → process):
  - `product`: productionproduct, productiontype, attribute, productattribute
  - `process`: processtype, processtypefield, stageextensiontype (dışa açık: `ProcessResources`)
  - `productionline`: line, stage, stageextension (dışa açık: `ProductionLineResources`)
  - `supplier`: zeytin tedarikçileri
- [x] `CatalogResources` → `ProcessResources` (proses alanları) + `ProductionLineResources` (aşama uzantıları); `ExtensionSync` → `ProductionStageExtensionService`; `StageOrder` → `ProductionStageService`
- [x] identity (auth, user, role, permission, security), inventory (storageunit, storagemovement, material), production (batch, batchprocess, batchprocess/stage), packaging (item, lot, supplier), finishedproduct (recipe, product, run, storage), sales (customer, order), analytics (analytics, report, dailysummary)
- Testler: tüm parity testleri ve `ModularityTests` her modülden sonra yeşil

## Faz 12: Canlıya Geçiş ve Laravel'in Kapatılması

Tüm modüller dev'de bittiği için geçiş bir kerede yapılır. Adım adım komutlar: [GO_LIVE.md](GO_LIVE.md).

**12a: Hazırlık (tamamlandı)**
- [x] Route karşılaştırması: Laravel'in 196 API route'unun hepsi Java'da; API dışındakiler (`/`, `/up`, `/sanctum/csrf-cookie`, `/storage/{path}`) uygulamada kullanılmıyor
- [x] Yerel proxy'de her `/api` yolu Java'da
- [x] Sunucu kurulumu: `deploy/compose.server.yaml` (Laravel'in `db` konteynerine bağlanır, aynı port), `deploy/deploy.sh`, `deploy/.env.server.example`
- [x] Şema karşılaştırma betiği: `deploy/compare-schema.sh` (yerel Laravel veritabanında "aynı", eklenen kolonda fark raporu)
- [x] Prova: sunucu compose'u yerelde Laravel'in veritabanıyla ayağa kalktı, 15 ekran Laravel'le aynı

**12b: Sunucuda geçiş (ertelendi; Bölüm B bittikten sonra yapılacak)**

Sunucudaki Laravel -> Java değişimi bu yol haritasının parçası olmaktan çıktı. Kod geçişi tamamlanmış
sayılır; hedef artık aşağıdaki Bölüm B.

**Bölüm A bu noktada biter: API tamamen Java'da, davranış Laravel ile birebir aynı.**

---

# Bölüm B: Tamamen Java Mimarisi

Bölüm A, Laravel'i Java'da birebir taklit etti: tipsiz `Row`/`Map`, `"required|string"` kural metinleri,
PHP tip dönüşümleri, Laravel'in cevap biçimleri. Bölüm B bunların hepsini Java'nın ve Spring'in kendi
yöntemleriyle değiştirir.

Kurallar:
- **API sözleşmesi korunmak zorunda değil.** Flutter uygulaması yeni Java API'sine göre ayrıca yeniden
  düzenlenecek; bu yol haritasındaki kararlar Flutter'a göre verilmez.
- **Aynı anda tek modül.** Bir modül bitmeden sonrakine geçilmez, her adımda `./mvnw verify` geçer.
- **Parity testleri güvenlik ağıdır.** B7'ye kadar kalırlar. Bilinçli bir davranış değişikliği olursa
  ilgili fixture aynı commit'te güncellenir.
- **İş kuralları değişmez.** Yalnızca teknik yapı değişir; yeni özellik eklenmez.
- **PATCH'te `null`:** varsayılan olarak "değiştirme" demektir. Gerçekten temizlenebilen alanlarda
  (örn. `description`) "gelmedi" ile "`null` geldi" ayrımı bir sarmalayıcı tipiyle korunur.

## B0: Geçiş kalıntılarının temizliği
- [x] `compose.laravel.yaml`, `application-laravel.yaml`, `docker/proxy/` silindi
- [x] `laravel` profili kontrolü (`SeedCommand`), `.env.example`'daki geçiş bölümü, README'deki
  "Geçiş dönemi" bölümü kaldırıldı

## B1: Pilot modül (`process/processtype`)
Yeni kalıp en küçük modülde baştan sona kurulur, diğer modüller bu örneğe göre taşınır.
- [x] JPA entity ve repository (`Row`/`Db` yerine), ortak `AuditedEntity`
- [x] Request `record` + Bean Validation (`@Valid`, `RequestInput` ve kural metinleri yerine)
- [x] Response `record` (`r.put` yerine); diğer modüllere açık tipler modül kökünde
- [x] Kategori `enum` + `AttributeConverter`
- [x] `@PathVariable long id` + serviste `find` (`@Bound` yerine)
- [x] `@Transactional` servis katmanında
- [x] Temizlenebilen alanlar için `Patch<T>`

Bu kalıpla gelen davranışlar:
- Yetki (403) kayıt aramasından (404) önce çalışır
- Tip dönüşümleri katıdır: `is_active` için `"1"` kabul edilmez, `true`/`false` gönderilir
- Bean Validation controller'a girmeden çalışır: izinsiz kullanıcı geçersiz gövdeyle 403 yerine 422 alır
  (B3'te Spring Security ile yetki öne alınır)
- Enum, veritabanındaki bütün değerleri içermeli: `machine_process` yalnızca eski kayıtlarda var, okunur
  ama yeni kayda yazılamaz. Taşımadan önce her enum için tablodaki gerçek değerlere bakılır

## B2: Modül modül taşıma
Her modülde B1'in kalıbı uygulanır. Bunlara ek olarak:
- Durum metinleri ve geçiş `Map`'leri `enum`'a taşınır
- Kg, litre, oran gibi miktarlar `double` + `Php.round` yerine `BigDecimal` olur
- `Php.*` ve `Casts.*` kullanımları tiplerle birlikte kalkar
- `Relations` ile elle ilişki yükleme yerine JPA ilişkileri, `@EntityGraph` ya da join
- `@OneToMany` yalnızca modül içinde kurulur; modüller arasında id ve public API kullanılır

Sıra:
- [x] `process`
- [x] `productionline`
- [x] `product`
- [x] `supplier`
- [x] `notification`
- [x] `packaging`
- [x] `sales`
- [x] `finishedproduct`
- [x] `inventory`
- [x] `production`
- [x] `analytics` (entity'ler JPA; ağır raporlar SQL + `record` olarak kalır)
- [x] `identity` (JPA'da; request/response ve `Row` kalıntıları)
- [x] `apprelease`
- [x] `Db`, `Row`, `Relations`, `Casts`, `Php`, `PhpDates`, `RequestInput`, `ValidatedInput`, `Rules`,
  `LaravelValidator`, `EmailRule`, `@Bound`, `@LaravelModel` silinir

## B3: Güvenlik
- [x] Spring Security: filtre zinciri ve `@PreAuthorize` (`AccessInterceptor`, `AccessAspect`,
  `@RequiresPermission`, `@RequiresRole` yerine)
- [x] OAuth2 Resource Server, NimbusJwtEncoder, RSA (`kid` ile)
- [x] Refresh token rotation (DB'de hash'li) ve reuse detection
- [x] Sanctum token doğrulaması kaldırılır
- [x] Rate limit: elle yazılmış `RateLimiter` ve `ThrottleInterceptor` yerine Bucket4j
- [x] İstek gövdesine boyut sınırı: `LoginBodyCachingFilter` giriş yapılmamış isteğin gövdesini sınırsız belleğe
  alıyor, limit kontrolünden önce çalışıyor; aşan istek reddedilir

## B4: Şema
- [ ] Şemanın sahibi Java: yeni migration'lar Flyway ile yazılır. Canlı veritabanına da uygulanacakları için
  veriyi koruyarak ileri yönlü çalışırlar
- [x] Laravel tabloları kaldırılır: `migrations`, `cache`, `cache_locks`, `jobs`, `sessions` vb.
- [x] Spatie tabloları (`model_has_roles`, `model_has_permissions`, `role_has_permissions`) ve
  `App\Models\...` metinleri Java'ya göre yeniden tasarlanır
- [x] `personal_access_tokens` kaldırılır (B3'te yapıldı)
- [ ] **Sunucu geçişinden sonra:** V0.xx dosyaları temiz başlangıç migration'larıyla değiştirilir ve
  `baseline-on-migrate` kaldırılır. Geçiş mevcut Laravel veritabanının üzerine yapıldığı için daha önce yapılamaz
- [x] Modüller arası tablo okumaları kaldırılır: başka modülün tablosunu `JdbcClient` ile okuyan
  yerler o modülün public API'sini kullanır (`ProductCatalog`, `NudgeDirectory`, `OrderStockQueries`,
  `SupplierDeliveryService` vb.); `product` ile `productionline` arasındaki bağımlılık döngüsü çözülür

## B5: JSON kolonlarının JSONB'ye geçişi
Tanımlar ve tipli değer tabloları (`value_decimal` vb.) ilişkisel kalır: yabancı anahtarlar, benzersiz kısıtlar
ve tank/tarif kuralları bunlara dayanır. Karar: 2026-11-10.
- [x] Zaten JSON tutan kolonlar (`operation_data`, `options`, `options_json`, `default_value_json`, `value_json`,
  `config` vb.) `jsonb` olur, Hibernate JSON eşlemesine geçer
- [x] Mevcut veri Flyway migration'ı ile korunur

## B6: API sözleşmesi
- [x] Sayfalama: Spring `Pageable` / `Page` (`LaravelPaginator`, `Listing` yerine)
- [x] Hatalar: `ProblemDetail` (RFC 9457), `ApiExceptionHandler` sadeleşir, PHP sınıf adları kalkar
- [x] Mesajlar: `messages.properties` (`LaravelMessages` yerine)
- [x] Standart Jackson ve ISO tarih (`LaravelDateTimeSerializer`, `LaravelStringDeserializer`,
  `InputNormalizationFilter`, `PhpNumbers` kalkar)

## B7: Testler
- [x] Laravel'den yakalanmış fixture'lara bağlı `*ParityIT`'ler, uygulamanın kendi davranışını
  tanımlayan normal testlere çevrilir: iş kuralları `*RulesIT`'lerde, uçtan uca senaryolar
  `src/test/resources/scenarios/` + `*ScenarioIT`
- [x] `src/test/resources/laravel/`, `reference/capture_*.py`, `reference/compare.py` kaldırılır
- [x] HTTP testleri tek Spring context'ini ve konteyneri paylaşır, her sınıf temiz demo veriyle başlar

## B8: Demo veri
- [ ] Demo fabrika akışının Java seeder'ları: veri bugünün tarihine göre üretilir
- [ ] `--seed` dökümü değil seeder'ları çalıştırır; `reference/` kaldırılır

## B9: Gözlemlenebilirlik
- [ ] Detaylı Grafana dashboard'ları ve alarm kuralları

## B10: Dokümantasyon
- [ ] springdoc-openapi ile API dokümantasyonu
- [ ] `docs/` güncellenir; `docs/laravel-reference/` kaldırılır, koddaki Laravel açıklamaları temizlenir

## B11: Kabul kriteri
- [ ] ArchUnit testi: `Php`, `Db`, `Row`, `RequestInput`, `LaravelValidator` ya da adında `Laravel`
  geçen bir sınıf kullanılırsa build düşer

**Bölüm B bu noktada biter: API'de Laravel'den kalan yapı yoktur ve bunu build doğrular.**

---

# Bölüm C: Tank Materyal Modeli

**Sorun:** Tanktaki materyaller tank %100 bitene kadar kapanmıyor. Her kısmi dolum, tankta kaç materyal
varsa o kadar çocuk materyal, koşu satırı ve hareket üretiyor. 500 dolumdan sonra bile hiçbir girdi bitmiş
sayılmıyor.

**Hedef akış:** ham ürün → üretim aşamaları → ön dinlendirme tankı → son tank → dolum → depo → satış

**Zamanlama:** Davranışı bilerek değiştirdiği için ayrı bir özellik olarak B7'den sonra, B8'den ve sunucu
geçişinden önce yapılır.

## C1: Tank türleri
- [x] Tankın türü düzenlenebilir bir alan olur: normal (son tank) ya da ön dinlendirme (`kind`: `final`,
  `pre_settling`; V7)
- [x] Tür iki yönde de değişebilir, ama yalnızca tank boşken; doluysa işlem reddedilir
- [x] Son tanktan ön dinlendirme tankına aktarım yapılamaz (tanklar arası transfer)

## C2: Son tank
- [x] Tank ve ürün tipi başına tek aktif tank materyali olur; `materials` tablosunda tutulur, partisi boştur
- [x] Tanka giren materyal, giren miktar kadar o anda kapanır; kısmi girişte kalan kısım üretimde aktif kalır
- [x] Aynı tip yeniden girerse miktar aynı satıra eklenir, farklı tip yeni tank materyali açar
- [x] Sayısal özellikler kütleye göre ağırlıklı ortalamayla güncellenir (litre yoğunlukla kg'ye çevrilir);
  diğerleri aynıysa kalır, farklıysa boşalır. Dolum ve çıkış ortalamayı değiştirmez
- [x] Ürünün zorunlu (`is_required`) özelliklerinden biri ölçülmemiş materyal son tanka giremez
- [x] Son tanka isteğe bağlı analiz girilebilir: ölçülen değer hesaplanan ortalamanın yerine geçer,
  sonraki girişler bu değerden devam eder
- [x] Her tank ölçümü geçmişe yazılır: hesaplanan değer, ölçülen değer, fark, ölçen kişi ve zaman.
  Sapma, hesabın ve ölçümlerin ne kadar tuttuğunu izlemek için saklanır
- [x] Tank materyali sıfırlanınca kapanır, sonraki giriş yeni bir tane açar
- [x] Tanktan çıkışta çocuk materyal açılmaz, tank materyali doğrudan azalır
- [x] Dolum, elle çıkış, tanktan tanka transfer ve sayım azalışı tipler arasında oransal düşer;
  yuvarlama farkı son tipe yazılır
- [x] Transferde hedef tank karışımdaki her tipi kabul etmeli, biri reddedilirse transferin tamamı reddedilir
- [x] Tank sayfasındaki giriş, çıkış, transfer ve sayım hareketleri normal tanklar için kalır
- [x] Üretim içinden son tanktaki karışım materyali üzerinde çıkış ya da transfer yapılamaz (tek partisi yok)

## C3: Ön dinlendirme tankı
- [x] Materyaller birleşmez, parti başına satırlar korunur ve tank ekranında tek tek görünür
- [x] Tek ürün tipiyle çalışır: farklı partiler girebilir, farklı tip giremez; blend ve çoklu ürün/tip ayarları bu
  türde açılamaz (V7 canlıdaki dinlenme tanklarında kapatır)
- [x] Giriş özel üretim aşamasıyla yapılır: yeni `pre_settling` uzantı tipi (V9). Giriş onaya düşer, onaylanınca aşama
  `resting` (dinlenmede) durumunda bekler; parti üretimde sayılır, sonraki aşamalar bekler
- [x] Bir parti aynı ön dinlendirme tankına bir kez girer; miktar yanlışsa aşama iptal edilip yeniden gönderilir
- [x] Boşaltmadan önce üstüne yeni parti eklenebilir
- [x] Partiye işlenen analizler bu tankta çalışmaya devam eder
- [x] Ön dinlendirme tankında elle giriş, çıkış, sayım ve transfer (tanka ya da tanktan) yapılamaz; üretimdeki depo
  hareketi uzantısı ve dolum da bu tanka dokunamaz. Çıkış yalnızca "Boşalt" ile
- [x] Yanlış aktarım boşaltmadan önce aşama iptal edilerek (`resting` → `waiting`, geri açma izni) geri alınır,
  materyal partiye döner (girerken bölünen parça partideki materyale geri eklenir); boşaltmadan sonra geri alma yoktur
- [x] Demo hatta "Ön Dinlenme" aşamasının depo hareketi ve elle pirina girişi kalktı, yerine `pre_settling` uzantısı
  geldi (çıktı ürünü Pirina). "Tanklarda Toplama" geçmişi olmayan hatlardan (2, 3) silindi; geçmişi olan hat 1'de
  silinemediği için uzantısız ve isteğe bağlı kaldı. "Filtreleme"nin yağ tartımı kalktı: yağ Boşalt'ta son tanka
  geçtiği için tartım partide sahte yağ materyali açardı; aşama alanlarıyla kalıyor

## C4: Boşaltma
- [x] Tek işlemlik "Boşalt" (`POST /api/storage-units/{id}/empty`, `storage_movement.transfer` izni, onaysız):
  hedef bir ya da birden çok son tank ve her biri için çekilen miktar (tankın kapasite biriminde) girilir
- [x] Girdi materyalleri %100 kapanır, onları tanka sokan dinlenmedeki aşamalar aynı işlemde tamamlanır
- [x] Çekilen miktar girdilere miktarlarına göre oransal, hedeflere istenen miktarlarla dağılır ve hedef tankların
  tip materyaline eklenir (son tank kuralları: izinli tip, yoğunluk, kapasite, ölçülmüş olma, tank uyumu)
- [x] İşlem tek parçadır: bir hedef kabul etmezse hiçbir şey kapanmaz
- [x] Çekilen giren kütleyi %0,5'ten fazla aşarsa reddedilir; tolerans içindeyse girenin tamamı çekilmiş sayılır

## C5: Tortu
- [x] Giren ile çekilen arasındaki fark kütle üzerinden hesaplanır, elle girilmez; fark sıfırsa tortu açılmaz
- [x] Fark gerçek bir aktif çıktı materyali olur, ne yapılacağına fabrika karar verir
- [x] Birden çok parti varsa tortu, giriş payına göre parti başına ayrı materyal olarak açılır (kapanan yağ
  materyalinin çocuğu); kütle aşamanın verisine `residue_kg` olarak yazılır
- [x] Tortunun hangi ürün olacağı aşama ayarındaki çıktı ürününden belirlenir, kodda sabit olmaz
- [x] Analizde tortu ayrı kalem (`residue_kg`, "Tortu"); pres sonrası pirinaya eklenmez
- [x] Ön dinlendirme aşamasıyla girmemiş (eski) materyaller Boşalt'ta tortusuz kapanır

## C6: Birimler
- [x] Litre tanka kg ürün girebilir (ve tersi); birim farkı yoğunlukla çevrilir, ton birimi yoktur
- [x] Tankın sabit bir temel birimi olmaz, tank materyali ürün tipinin kendi biriminde kalır
- [x] Kapasite ve içerik hem kg hem litre gösterilir, kapasite kontrolü çevrilmiş miktarla yapılır
- [x] Yoğunluğu tanımlı olmayan ürün tipi tanka giremez (birim aynı olsa bile; tipin kendi yoğunluğu, yoksa ürünün
  varsayılanı)
- [x] Boş tankta diğer birimdeki karşılık: aday tipler (izinli tipler, yoksa izinli ürünlerin tipleri) tek bir ürüne
  aitse o ürünün varsayılan yoğunluğundan hesaplanır; farklı ürünlerden geliyorsa, varsayılan yoksa ya da aday
  yoksa yalnızca girilen birim gösterilir. Dolu tankta karışımın yoğunluğu (toplam kg / toplam litre) kullanılır

## C7: İzlenebilirlik ve eski veri
- [x] Tanka giriş hareketi, karıştığı tank materyalini gösterir; soy grafiği parti → tank materyali → dolum →
  depo → sipariş şeklinde yürür
- [ ] Tanklardaki mevcut aktif materyaller tank ve tip bazında birleştirilir, ortalamalar hesaplanır, eskiler kapanır
- [x] Canlıda dinlenme tankları yalnızca metinle belirtiliyor: V7, kodunda, adında ya da açıklamasında "dinlen"
  geçen tankları `pre_settling` yapar; liste geçişten önce GO_LIVE 2. adımda kontrol edilir
- [ ] Üretim aşamalarında sabitlenmiş eski `outbound` ve `transfer` ayarları geçişten önce canlı veride kontrol edilir

## C8: Geliştirme başlarken konuşulacaklar
- [x] Hepsi 2026-11-11'de konuşuldu, kararlar C1-C7'de

---

## Bağımlılık sırası (özet)

```
Bölüm A: Kod Geçişi (tamamlandı)
  Faz 0-11  Altyapı ve bütün modüller
  Faz 12    Hazırlık yapıldı; sunucu geçişi kapatıldı

Bölüm B: Tamamen Java Mimarisi
  B0 Temizlik
  B1 Pilot ─► B2 Modül modül taşıma
  B3 Güvenlik ─► B4 Şema ─► B5 JSONB
  B6 API sözleşmesi ◄─ B2
  B7 Testler ◄─ B2, B6
  B8 Demo veri ◄─ B4
  B9 Dashboard'lar    B10 Doküman
  B11 Kabul kriteri ◄─ hepsi

Bölüm C: Tank Materyal Modeli ◄─ B7 (B8 ve sunucu geçişinden önce)
  C1 Tank türleri ─► C2 Son tank ─► C3 Ön dinlendirme ─► C4 Boşaltma ─► C5 Tortu
  C6 Birimler    C7 İzlenebilirlik ve eski veri    C8 Konuşulacaklar
```

## Genel kurallar (Bölüm A)
Bölüm B'nin kuralları kendi başlığında.
- **Aynı anda tek modül.** Bir modül tamamlanma kriterini geçmeden bir sonrakine başlanmaz.
- **Java hiçbir zaman Laravel'e bağımlı olmaz.** Varsayılan kurulum tek başına; Laravel'le ilgili her şey `laravel` profilinde ve geçiş dönemine ait dosyalarda durur.
- **Geçiş süresince yeni özellik yok.** Laravel'e yeni özellik eklenirse Java'ya da eklenmesi gerekir. Zorunlu bir değişiklik olursa önce taşınmış tarafta yapılır.
- **Şemanın sahibi geçiş boyunca Laravel'dir.** Laravel'in tablolarına Java migration yazmaz. Java yalnızca kendi altyapı tablolarını (Laravel'in kullanmadığı, örn. `event_publication`) ekleyebilir.
- **Laravel'den her fark ya düzeltilir ya da [DEVIATIONS.md](laravel-reference/DEVIATIONS.md)'ye yazılır.**
- **İş mantığı yeniden tasarlanmaz, birebir taşınır.** İyileştirmeler Bölüm B'de ya da sonrasında yapılır. Aksi halde bir hatanın taşımadan mı yoksa yeni tasarımdan mı geldiği anlaşılmaz.
- **Laravel `dev` branch'ine yeni commit gelirse** ilgili fazlar `ba61990` sonrasındaki farka bakılarak güncellenir.
- **Her modülün workflow dokümanı** (`docs/workflow-*.md`) o fazın şartnamesi gibi kullanılır.
