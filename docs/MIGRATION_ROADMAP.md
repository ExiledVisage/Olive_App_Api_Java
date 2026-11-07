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
- [x] **Flyway baseline:** `V1__baseline.sql` = `reference/schema.sql` (psql komutları ve oturum ayarları çıkarıldı). Mevcut Laravel veritabanında V1 çalışmaz, baseline kabul edilir; boş veritabanında çalışır. `BaselineSchemaIT` Flyway + seed ile kurulan veritabanının Laravel'inkiyle şema (kolon, constraint, index), satır sayıları ve sequence'ler düzeyinde aynı olduğunu doğrular. Geçiş bitene kadar Laravel tablolarındaki değişiklikler **sadece Laravel migration'larıyla** yapılır; Java sadece kendi altyapı tablolarını ekleyebilir (`V2`: Modulith `event_publication`)
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

## Faz 12: Canlıya Geçiş ve Laravel'in Kapatılması

Canlıya geçiş modül modül de yapılabilir (her modül dev'de bitince canlıda da açılır) ya da tüm modüller dev'de bitince bir kerede. Her iki durumda da:
- [ ] **Şema kontrolü:** Sunucudaki canlı şema (`pg_dump --schema-only`) `V1__baseline.sql` ile karşılaştırılır. Elle yapılmış bir değişiklik ya da çalıştırılmamış bir migration varsa önce o giderilir. Bunun için veriye değil, sadece şemaya bakılır
- [ ] **Her geçişten önce canlı veritabanının yedeği alınır**
- [ ] Java canlı veritabanına bağlanır. Flyway `baselineOnMigrate` ile mevcut şemayı kabul eder, hiçbir tabloya dokunmaz
- [ ] Canlıda proxy yönlendirmeleri açılır, Grafana'dan izlenir
- [ ] Tüm trafik Java'da, Laravel'e giden istek yok (proxy log'larıyla doğrulanır)
- [ ] Laravel konteyneri kapatılır, repo arşivlenir
- [ ] Birlikte çalışma düzeni silinir: `compose.laravel.yaml`, `application-laravel.yaml`, `docker/proxy/`, `.env.example`'daki geçiş bölümü, README'deki "Geçiş dönemi" bölümü

**Bölüm A bu noktada biter: API tamamen Java'da, davranış Laravel ile birebir aynı.**

---

# Bölüm B: Geçiş Sonrası

Laravel kapandıktan sonra yapılır. Bu işlerin hepsi ortak olan bir şeyi (şema, API sözleşmesi ya da auth) değiştirdiği için geçiş sırasında yapılamaz. Her biri ayrı ayrı ve kendi testleriyle yapılır.

## B1: Şema Sahipliği
- [ ] Şemanın sahibi artık Java: yeni migration'lar Flyway ile yazılır
- [ ] Laravel'e ait tablolar temizlenir (`cache`, `jobs`, `sessions`, `migrations` vb.)

## B2: Auth: JWT + Refresh Token Rotation
- [ ] OAuth2 Resource Server, NimbusJwtEncoder, RSA (`kid` ile)
- [ ] Refresh token rotation (DB'de hash'li) ve reuse detection
- [ ] Faz 2'deki doğrulama arayüzünün arkasına JWT konulur, Sanctum doğrulaması kaldırılır
- [ ] Frontend'de login ve token yenileme akışı güncellenir (frontend ile birlikte yayınlanır)
- [ ] `personal_access_tokens` tablosu kaldırılır

## B3: Dinamik Alanların JSONB'ye Geçişi
- [ ] Attribute definition, process type field, stage extension value vb. yapıların Postgres JSONB + Hibernate JSON mapping'e taşınması
- [ ] Mevcut verinin Flyway migration'ı ile dönüştürülmesi
- [ ] API cevapları değişmemeli: sadece iç yapı değişir

## B4: Demo Seeder'lar
Şu an `--seed`, Laravel'in seeder'larının ürettiği verinin dökümünü (`reference/seed-data.sql`) yüklüyor. Tarihler o günde (2026-11-06) sabit.
- [ ] `OliveOilProductionFlowSeeder` ve demo fabrika akışının Java karşılığı: veriyi bugünün tarihine göre üretir
- [ ] `--seed` dökümü değil Java seeder'larını çalıştırır; `reference/seed-data.sql` kaldırılır

## B5: Gözlemlenebilirlik
- [ ] Detaylı Grafana dashboard'ları ve alarm kuralları

## B6: Dokümantasyon
- [ ] `docs/` dokümanlarının Java projesine taşınması ve güncellenmesi (`project-overview.md` dahil)
- [ ] springdoc-openapi ile API dokümantasyonu

---

## Bağımlılık sırası (özet)

```
Bölüm A: Kod Geçişi
  Faz 0  Yerel ortam + Laravel referans sistem + dondurulmuş veri + kararlar
  Faz 1  İskelet + ortak altyapı + test altyapısı + gözlemlenebilirlik
  Faz 2  identity ─────────────┐ (her şey buna bağlı)
  Faz 3  app-release           │
  Faz 4  catalog ◄─────────────┤
  Faz 5  inventory ◄─ catalog  │
  Faz 6  production ◄─ catalog, inventory
  Faz 7  packaging ◄─ catalog (supplier)
  Faz 8  finished-product ◄─ inventory, production, packaging
  Faz 9  sales ◄─ finished-product
  Faz 10 notification ◄─ (event'lerle herkesi dinler)
  Faz 11 analytics ◄─ (herkesi okur)
  Faz 12 Canlıya geçiş + Laravel'in kapatılması

Bölüm B: Geçiş Sonrası
  B1 Şema sahipliği ◄─ (B2 ve B3 bunu gerektirir)
  B2 JWT        B3 JSONB        B4 Seeder'lar        B5 Dashboard'lar        B6 Doküman
```

## Genel kurallar
- **Aynı anda tek modül.** Bir modül tamamlanma kriterini geçmeden bir sonrakine başlanmaz.
- **Java hiçbir zaman Laravel'e bağımlı olmaz.** Varsayılan kurulum tek başına; Laravel'le ilgili her şey `laravel` profilinde ve geçiş dönemine ait dosyalarda durur.
- **Geçiş süresince yeni özellik yok.** Laravel'e yeni özellik eklenirse Java'ya da eklenmesi gerekir. Zorunlu bir değişiklik olursa önce taşınmış tarafta yapılır.
- **Şemanın sahibi geçiş boyunca Laravel'dir.** Laravel'in tablolarına Java migration yazmaz. Java yalnızca kendi altyapı tablolarını (Laravel'in kullanmadığı, örn. `event_publication`) ekleyebilir.
- **Laravel'den her fark ya düzeltilir ya da [DEVIATIONS.md](laravel-reference/DEVIATIONS.md)'ye yazılır.**
- **İş mantığı yeniden tasarlanmaz, birebir taşınır.** İyileştirmeler Bölüm B'de ya da sonrasında yapılır. Aksi halde bir hatanın taşımadan mı yoksa yeni tasarımdan mı geldiği anlaşılmaz.
- **Laravel `dev` branch'ine yeni commit gelirse** ilgili fazlar `ba61990` sonrasındaki farka bakılarak güncellenir.
- **Her modülün workflow dokümanı** (`docs/workflow-*.md`) o fazın şartnamesi gibi kullanılır.
