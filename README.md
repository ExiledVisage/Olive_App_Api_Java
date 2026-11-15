# Olive API (Java)

Olive Production Tracking backend'i. Bu repo **tek başına** çalışır.

- Yol haritası: [docs/MIGRATION_ROADMAP.md](docs/MIGRATION_ROADMAP.md)
- Teknolojiler: [docs/TECH_STACK.md](docs/TECH_STACK.md)
- Sunucu kurulumu: [docs/SERVER.md](docs/SERVER.md)
- API dokümantasyonu: uygulama `./mvnw spring-boot:run` ile (dev profili) açıkken
  http://localhost:8081/swagger-ui.html (JSON: `/v3/api-docs`). Sunucuda kapalı.

## Gereksinimler

- JDK 25
- PostgreSQL (bilgisayara kurulu) **ya da** Docker Desktop
- Docker Desktop, Mailpit / Prometheus / Grafana için de kullanılır (isteğe bağlı)

## Kurulum (yeni bilgisayar)

```bash
git clone https://github.com/ExiledVisage/Olive_App_Api_Java.git
cd Olive_App_Api_Java
cp .env.example .env
```

**Veritabanı**, iki seçenekten biri:

- **(A) Bilgisayara kurulu PostgreSQL:** bir kez rol ve veritabanını oluşturun, `.env`'deki `DB_*` değerleriyle aynı olmalı:
  ```bash
  psql -U postgres -c "CREATE ROLE oliveapp LOGIN PASSWORD 'oliveapp'"
  psql -U postgres -c "CREATE DATABASE oliveapp OWNER oliveapp"
  ```
- **(B) Docker'daki PostgreSQL:** `.env`'de `COMPOSE_PROFILES=docker-db` satırını açın. Başka bir şey gerekmez.

**Demo veri ve çalıştırma:**

```bash
# Şemayı kurar (Flyway), bugün biten bir yıllık demo veriyi üretir, sonra kapanır. Veritabanı boş olmalı.
./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed

# Uygulama
./mvnw spring-boot:run
```

Veritabanını baştan kurmak için: `-Dspring-boot.run.arguments="--seed --fresh"` (yalnızca yerel adreslerde çalışır).
Eski migration zinciriyle (V0.xx, V2-V11) kurulmuş bir veritabanında uygulama Flyway doğrulamasında durur, `--fresh`
de çalışamaz; şema elle boşaltılıp seed yeniden yüklenir:

```bash
psql -U oliveapp -d oliveapp -c "DROP SCHEMA public CASCADE" -c "CREATE SCHEMA public"
# Docker'daki PostgreSQL ise: docker compose exec postgres psql -U oliveapp -d oliveapp -c "..."
```

| Adres | Ne |
|---|---|
| http://localhost:8081 | Java API |
| http://localhost:8081/swagger-ui.html | API dokümantasyonu (dev profili) |
| http://localhost:3000 | Grafana, `admin` / `admin`. "Olive API" dashboard'u |
| http://localhost:9090 | Prometheus |
| http://localhost:8025 | Mailpit |

Docker Desktop çalışıyorsa Mailpit, Prometheus ve Grafana uygulama açılırken otomatik başlar.
Docker olmadan çalıştırmak için `.env`'e `SPRING_DOCKER_COMPOSE_ENABLED=false` ekleyin
(bu durumda veritabanı seçenek A olmalı).

Java'yı da konteynerde çalıştırmak için: `docker compose --profile container up -d --build`.

### Demo veri

Bugün biten bir yıllık demo fabrika (`devdata`): yaklaşık 290 üretim partisi, 480 dolum koşusu,
335 sipariş, 7 kullanıcı (şifreleri `123`, örn. `admin@olivamore.com`). Tanımlar (ürünler, hatlar,
tanklar, tarifler, müşteriler) `src/main/resources/devdata/master-data.sql`'den yüklenir; akış
(partiler, ön dinlendirme ve Boşalt, dolum, depo, sipariş) gün gün, çalışan uygulamanın API'si
üzerinden yürütülür, bu yüzden her zaman güncel iş kurallarına uyar. Ayrıntı: `DemoFactory`.
Yükleme birkaç dakika sürer.

### Migration'lar

Şema `src/main/resources/db/migration/` altında, modül klasörlerinde, tablo başına bir dosya (Flyway alt klasörleri
de tarar). Sürüm `V<modül sırası>_<tablo sırası>`: `shared` 1, `identity` 2, `product` 3, `process` 4,
`productionline` 5, `supplier` 6, `inventory` 7, `production` 8, `packaging` 9, `finishedproduct` 10, `sales` 11,
`notification` 12, `analytics` 13. Örn. `identity/V2_03__create_permissions.sql`. Her dosyada tablonun kendisi,
sequence'i, index ve constraint'leri, foreign key'leri ve kodda tanımlı referans satırları (izinler, admin rolü,
aşama uzantı tipleri) vardır. Bir foreign key, iki tablodan sonra oluşanın dosyasındadır. Demo veri bunların
üzerine `devdata/master-data.sql`'den gelir.

Yeni migration (yeni tablo ya da var olan tabloda değişiklik) modülün klasörüne, bütün sürümlerden büyük bir
sürümle eklenir: örn. `inventory/V14_01__create_tank_cleanings.sql`, `inventory/V14_02__add_cleaned_at_to_storage_units.sql`.
Daha küçük bir sürüm (`inventory/V7_10__...`) dolu veritabanında sıra dışı sayılır ve uygulama açılmaz. Uygulanmış bir
dosya değiştirilmez (Flyway checksum'ı tutmaz); geliştirme aşamasında bütün veritabanları sıfırlanacaksa
(`--seed --fresh`, sunucuda `down -v`) tablonun kendi dosyası düzenlenebilir.

## Testler

```bash
./mvnw test      # birim testleri (*Test, *Tests)
./mvnw verify    # + integration testleri (*IT), Docker gerekir
```

Integration testleri Testcontainers ile boş bir Postgres 17 açar ve Flyway şemayı kurar. Demo veri
`--seed` ile aynı yoldan, 2026-11-06'da biten ve sabit tohumlu olarak bir kez üretilir, bellekte tutulur
ve her test sınıfından önce geri yüklenir (`DemoSnapshot`).

## Kod düzeni

```
com.olivamore.api
├── shared/        Ortak altyapı (OPEN modül): ProblemDetail hataları, doğrulama mesajları, sayfalama,
│                  JSON, zaman damgaları, rate limiting, silme koruması, OpenAPI
├── identity/        auth, user, role, permission, security (JWT, refresh token, izin kontrolü)
├── product/         productionproduct, productiontype, attribute, productattribute
├── process/         processtype, processtypefield, stageextensiontype
├── productionline/  line, stage, stageextension
├── supplier/        Zeytin tedarikçileri, tarlaları ve teslimatları
├── inventory/       storageunit, storagemovement, material
├── production/      batch, batchprocess (+ stage: tartım, dönüşüm, depo hareketi, analiz)
├── packaging/       item, lot, supplier (ambalaj tedarikçileri)
├── finishedproduct/ recipe, product, run, storage
├── sales/           customer, order
├── notification/    Bildirimler
├── analytics/       analytics, report, dailysummary
├── apprelease/      Mobil uygulama sürümleri
└── devdata/         Demo fabrika verisi (--seed)
```

Her konu klasöründe `XxxController`, `XxxService`, entity ve repository'ler, istek record'ları
(`CreateXxxRequest`, `UpdateXxxRequest`) ve cevaplar (`XxxResponse`, `XxxResponses`) bulunur. Alt klasörler modülün içidir; başka bir modülün kullanabildiği sınıflar
yalnızca modül kökündedir (ör. `productionline.ProductionLineResources`, `process.ProcessResources`,
`inventory.MaterialResources`). Spring Modulith bunu ve modüller arası döngü olmamasını `ModularityTests`
ile doğrular.

API sözleşmesi:

```java
@RestController
class OrderController {

    @GetMapping("/api/orders")
    @PreAuthorize("hasAuthority('order.view')")
    PagedModel<OrderResponse> index(@RequestParam(required = false) String status, Pageable pageable) {
        return Pages.of(pageable, responses::page, () -> responses.list(status));
    }

    @PostMapping("/api/orders")
    @PreAuthorize("hasAuthority('order.create')")
    ResponseEntity<DataResponse<OrderResponse>> store(@Valid @RequestBody CreateOrderRequest request) {
        return DataResponse.created(...);                                // 201
    }
}

record CreateOrderRequest(
    @NotNull(message = MessageKeys.REQUIRED) Long customerId,
    @Size(max = 1000, message = MessageKeys.SIZE_MAX) String note) {

    CreateOrderRequest {
        note = Texts.trim(note);                                         // kırpılır, boş metin null
    }
}
```

- **Girdi:** JSON gövdesi `@Valid @RequestBody` ile istek record'una, query parametreleri `@RequestParam` /
  `@ModelAttribute` ile bağlanır (query parametreleri kırpılır, boş değer verilmemiş sayılır). Metin alanları
  record kurucusunda `Texts.trim` ile temizlenir (şifre ve token hariç). Boolean alanlar yalnızca `true`/`false`,
  metin alanına sayı gelmez. Tarih-saat ISO-8601 (`2026-11-06T18:35:14+03:00`, offset'siz değer UTC kabul edilir).
- **Cevaplar:** tek kayıt `{"data": {...}}` (`DataResponse`), sayfasız liste `{"data": [...]}` (`ListResponse`),
  sayfalı liste Spring Data `PagedModel`: `{"content": [...], "page": {"size", "number", "total_elements",
  "total_pages"}}`. Sayfa `?page=0&size=20` (0 tabanlı, en fazla 200, ikisi birlikte); verilmezse bütün kayıtlar.
  Tarihler ISO-8601 UTC (`2026-11-06T15:35:14Z`), sayılar JSON sayısı.
- **Hatalar:** RFC 9457 `ProblemDetail` (`application/problem+json`):
  `{"title": "Unprocessable Content", "status": 422, "detail": "...", "instance": "/api/orders", "errors": {"alan": ["..."]}}`.
  `errors` yalnızca alan hatalarında. `ValidationFailedException` (alan hataları), `BusinessRuleException` (422),
  `HttpStatusException` (durum + mesaj), `ModelNotFoundException` (404, `Order 7 not found.`).
- **Mesajlar:** `messages.properties`; anotasyonlarda `MessageKeys` (`{validation.required}`), `{attribute}` alanın adı.
- **Rate limit:** `@Throttle("login")` (adlandırılmış, `NamedLimiter` bean'i) ya da `@Throttle(maxAttempts = 30, decayMinutes = 1)`.
- **Silme:** `DeletionGuard.of("...").usedBy("...", n).check()`
- **Testler:** iş kuralları `*RulesIT` sınıflarında tek tek; uçtan uca API davranışı `src/test/resources/scenarios/*.json`
  senaryolarında (`*ScenarioIT`, `ScenarioReplay`). HTTP testleri `@ApiIntegrationTest` ile tek Spring context'ini ve
  Postgres konteynerini paylaşır, her sınıf demo verinin temiz haliyle başlar. Bilinçli bir sözleşme değişikliğinden
  sonra senaryolar `-Dscenario.record=<klasör>` ile yeniden yazılabilir: tutmayan adımlar gelen cevapla güncellenir,
  fark gözden geçirilip senaryo dosyasına alınır.
- **Rate limit sayaçları** bellekte (tek uygulama örneği). Birden fazla örnekle çalışılacaksa ortak bir depoya
  (ör. Redis) taşınmalı.
- **Eşzamanlılık:** stok hareketinde işlemdeki tank(lar) transaction başında `SELECT ... FOR UPDATE` ile kilitlenir
  (transferde iki tank, id sırasıyla); aynı tanka eşzamanlı girişler tankı taşıramaz (`StorageMovementConcurrencyIT`).
- **Sıra:** her liste sabit bir sıralamayla döner (eşitlikte id); sırası tanımsız sorgu, yeniden üretilen demo veride
  kararsız teste dönüşür.
