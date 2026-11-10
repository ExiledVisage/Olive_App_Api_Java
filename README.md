# Olive API (Java)

Olive Production Tracking backend'i. Bu repo **tek başına** çalışır: Laravel gerekmez.

- Yol haritası: [docs/MIGRATION_ROADMAP.md](docs/MIGRATION_ROADMAP.md)
- Teknolojiler: [docs/TECH_STACK.md](docs/TECH_STACK.md)
- Laravel API envanteri: [docs/laravel-reference/ENDPOINTS.md](docs/laravel-reference/ENDPOINTS.md)
- Laravel'den bilinen farklar: [docs/laravel-reference/DEVIATIONS.md](docs/laravel-reference/DEVIATIONS.md)
- Referans veri ve karşılaştırma araçları: [reference/README.md](reference/README.md)

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
# Şemayı kurar (Flyway) ve demo veriyi yükler, sonra kapanır. Veritabanı boş olmalı.
./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed

# Uygulama
./mvnw spring-boot:run
```

Veritabanını baştan kurmak için: `-Dspring-boot.run.arguments="--seed --fresh"` (yalnızca yerel adreslerde çalışır).

| Adres | Ne |
|---|---|
| http://localhost:8081 | Java API |
| http://localhost:3000 | Grafana, `admin` / `admin`. "Olive API" dashboard'u |
| http://localhost:9090 | Prometheus |
| http://localhost:8025 | Mailpit |

Docker Desktop çalışıyorsa Mailpit, Prometheus ve Grafana uygulama açılırken otomatik başlar.
Docker olmadan çalıştırmak için `.env`'e `SPRING_DOCKER_COMPOSE_ENABLED=false` ekleyin
(bu durumda veritabanı seçenek A olmalı).

Java'yı da konteynerde çalıştırmak için: `docker compose --profile container up -d --build`.

### Demo veri

Laravel'in demo seeder'larının ürettiği fabrika akışı: 273 üretim partisi, 389 dolum koşusu,
335 sipariş, 7 kullanıcı (şifreleri `123`, örn. `admin@olivamore.com`). Kaynak:
`reference/seed-data.sql`. Veri 2025-11-06 – 2026-11-06 aralığına yayılmış durumda; Java
seeder'ları gelince (yol haritası B4) bugünün tarihine göre üretilecek.

## Testler

```bash
./mvnw test      # birim testleri (*Test, *Tests)
./mvnw verify    # + integration testleri (*IT), Docker gerekir
```

Integration testleri Testcontainers ile boş bir Postgres 17 açar, Flyway şemayı kurar ve
demo veri yüklenir; yani `--seed` ile aynı kurulum. `BaselineSchemaIT` sonucun Laravel'in
veritabanıyla (şema, her tablonun satır sayısı, sequence'ler) aynı olduğunu doğrular.

## Kod düzeni

```
com.olivamore.api
├── shared/        Ortak altyapı (OPEN modül): Laravel uyumlu hatalar, validator, sayfalama, JSON,
│                  model binding, rate limiting, silme koruması
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
└── devdata/         Demo veri yükleme (--seed)
```

Her konu klasöründe Laravel adlarıyla `XxxController`, `XxxService`, `XxxResource` ve `XxxRequests`
(FormRequest kuralları) bulunur. Alt klasörler modülün içidir; başka bir modülün kullanabildiği sınıflar
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
- **Doğrulama:** Laravel'den yakalanan davranış `reference/capture_*.py` ile, Java'ya `*ParityIT` ile tekrar oynatılır.
