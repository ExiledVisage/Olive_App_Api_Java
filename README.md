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
├── shared/        Ortak altyapı (OPEN modül): Laravel uyumlu hatalar, sayfalama, JSON, silme koruması
├── devdata/       Demo veri yükleme (--seed)
└── <modül>/       Faz 2'den itibaren: identity, catalog, inventory, production, ...
```

Laravel uyumluluğu için kurallar:
- **Hatalar:** `BusinessRuleException` (422), `ModelNotFoundException("Order", id)` (404), `LaravelValidationException` (422). Diğer her şey `ApiExceptionHandler`'da Laravel formatına çevrilir.
- **Validation mesajları:** `LaravelMessages` sabitleri, örneğin `@NotBlank(message = LaravelMessages.REQUIRED)`. DTO alanları Laravel'deki kuralların sırasıyla yazılır.
- **Query parametreleri:** Zorunlu olanlar `@Valid @ModelAttribute` bir record'da toplanır, snake_case adlar `@BindParam` ile bağlanır.
- **Listeler:** `Listing.of(...)`. `per_page` yoksa sayfasız, varsa Laravel'in sayfalı formatı.
- **Tarihler:** `Instant` / `LocalDateTime` otomatik olarak `2026-11-06T15:35:14.000000Z` formatında yazılır.
- **Silme:** `DeletionGuard.of("...").usedBy("...", n).check()`
- **Şema:** Geçiş boyunca Laravel'in tablolarına Java migration yazmaz. Java sadece kendi altyapı tabloları için migration ekleyebilir (V2: `event_publication`).

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
