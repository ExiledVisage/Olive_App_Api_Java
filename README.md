# Olive API (Java)

Olive Production Tracking backend'i. Laravel'den Java'ya taşınıyor; hedef Laravel'in
tamamen kalkması. Bu repo **tek başına** çalışır: Laravel gerekmez.

Geçiş sırasında Laravel'le aynı veritabanını kullanan ikinci bir çalışma modu da var
(aşağıda "Geçiş dönemi"). Laravel kapatılınca o mod silinir.

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
├── identity/      Kullanıcılar, roller, izinler, Sanctum uyumlu token'lar (Faz 2)
├── devdata/       Demo veri yükleme (--seed)
└── <modül>/       Sonraki fazlar: catalog, inventory, production, ...
```

Bir Laravel controller'ını taşırken:

```java
@RestController
@RequiresPermission("order.view")                // Laravel: ->middleware('permission:order.view')
class OrderController {

    @GetMapping("/api/orders/{order}")
    DataResponse<OrderResource> show(@Bound Order order) { ... }      // route model binding, yoksa 404

    @PostMapping("/api/orders")
    @RequiresPermission("order.create")
    ResponseEntity<DataResponse<OrderResource>> store(RequestInput input, CurrentUser user) {
        ValidatedInput v = validator.validate(input, Rules.of()        // FormRequest::rules(), aynı sırayla
            .field("customer_id", "required|integer|exists:customers,id")
            .field("items", "required|array|min:1")
            .field("items.*.quantity", "required|integer|min:1"));
        return DataResponse.created(...);                              // yeni model -> 201
    }
}
```

Laravel uyumluluğu için kurallar:
- **Giriş:** `/api/**` altındaki her endpoint varsayılan olarak giriş ister; giriş istemeyenler `@Public`. Rol/izin: `@RequiresRole`, `@RequiresPermission` (sınıf ve metot birlikte kullanılabilir, admin her izne sahip). Sıra Laravel'deki gibi 401 → 404 → 403.
- **Validation:** `LaravelValidator` + `Rules`, kurallar Laravel'deki metnin aynısı ve aynı sırada (hata sırası buna bağlı). Desteklenmeyen bir kural ilk kullanımda hata verir; Laravel'in `ValidatesAttributes` kodundan birebir eklenir. Bean Validation (`@Valid`) kullanılmaz.
- **Girdi:** `RequestInput` (JSON gövdesi + query, kırpılmış, boş metin null). `@RequestBody` kullanılmaz.
- **Hatalar:** `BusinessRuleException` (422 + `errors`), `HttpStatusException.unprocessable(...)` (`abort(422, ...)`, sadece `message`), `ModelNotFoundException` (404), `LaravelValidationException` (422).
- **Cevaplar:** tek kayıt `DataResponse.of(...)` / yeni kayıt `DataResponse.created(...)`, liste `Listing.of(...)` (`per_page` yoksa sayfasız).
- **Veritabanı:** Laravel tablolarına yazan modüller `Db` kullanır (JDBC, Eloquent davranışı: `create`, `update`, `whereIn` ile ilişki yükleme). Route model binding: `@Bound(table = "process_types", model = "ProcessType") Row processType`. JPA entity'leri (`@LaravelModel`) sadece identity'de. Zaman damgaları `Timestamps.now()` (UTC, saniyeye kırpılmış).
- **Resource'lar:** `Map` (LinkedHashMap) olarak kurulur; Laravel'de `whenLoaded` ile yüklenmemiş ilişkinin anahtarı yazılmaz. Cast'ler resource'ta (`decimal:4` → `"0.9150"`).
- **Rate limit:** `@Throttle("login")` (adlandırılmış, `NamedLimiter` bean'i) ya da `@Throttle(maxAttempts = 30, decayMinutes = 1)`.
- **Silme:** `DeletionGuard.of("...").usedBy("...", n).check()`
- **Şema:** Geçiş boyunca Laravel'in tablolarına Java migration yazmaz. Java sadece kendi altyapı tabloları için migration ekleyebilir (V2: `event_publication`).
- **Doğrulama:** Her modül için Laravel'in davranışı `reference/capture_*.py` ile yakalanır, Java'ya `*ParityIT` ile tekrar oynatılır (örnek: `IdentityParityIT`). Farklar ya düzeltilir ya [DEVIATIONS.md](docs/laravel-reference/DEVIATIONS.md)'ye yazılır.

---

## Geçiş dönemi: Laravel ile birlikte çalışma

> Laravel kapatılınca (Faz 12) bu bölüm, `compose.laravel.yaml` ve `application-laravel.yaml` silinir.

Java, Laravel'in veritabanına bağlanır; önlerindeki reverse proxy taşınan endpoint'leri
Java'ya, geri kalanı Laravel'e yönlendirir. Karşılaştırma ve test için kullanılır.

```bash
# 1. Laravel ayakta olmalı
cd ../Olive_Production_Tracking_App_Back/olive_oil_back && docker compose up -d && cd -

# 2. .env'e Laravel'in DB şifresini yazın: LARAVEL_DB_PASSWORD (olive_oil_back/.env içindeki DB_PASSWORD)

# 3. Java, laravel profiliyle
./mvnw spring-boot:run -Dspring-boot.run.profiles=laravel
```

| Adres | Ne |
|---|---|
| http://localhost:8080 | Reverse proxy. Frontend buraya bağlanır. `X-Served-By` başlığı hangi sistemin cevap verdiğini gösterir |
| http://localhost:8001 | Laravel (doğrudan) |
| localhost:5433 | Laravel'in Postgres'i (yalnızca bu bilgisayardan) |

`--seed` bu modda çalışmaz; Laravel'in veritabanı `reference/restore.sh` ile dondurulmuş
veriye döndürülür. Taşınan yollar `docker/proxy/java-routes.caddy`'ye eklenir.

```bash
# Laravel'i dondurulmuş veriye döndür, iki sistemi aynı anda karşılaştır
reference/restore.sh ../Olive_Production_Tracking_App_Back/olive_oil_back
python -I reference/compare.py ../Olive_Production_Tracking_App_Back/olive_oil_back --only api/app
```
