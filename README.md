# Olive API (Java)

Olive Production Tracking backend'inin Laravel'den Java'ya geçişi. Geçiş modül modül yapılıyor:
Laravel ve Java aynı veritabanını kullanıyor, önlerindeki reverse proxy taşınan endpoint'leri
Java'ya, geri kalanı Laravel'e yönlendiriyor.

- Yol haritası: [docs/MIGRATION_ROADMAP.md](docs/MIGRATION_ROADMAP.md)
- Teknolojiler: [docs/TECH_STACK.md](docs/TECH_STACK.md)
- Laravel API envanteri: [docs/laravel-reference/ENDPOINTS.md](docs/laravel-reference/ENDPOINTS.md)
- Laravel'den bilinen farklar: [docs/laravel-reference/DEVIATIONS.md](docs/laravel-reference/DEVIATIONS.md)
- Referans veri ve karşılaştırma araçları: [reference/README.md](reference/README.md)

## Gereksinimler

- JDK 25
- Docker Desktop
- Python 3 (sadece `reference/` araçları için)
- Laravel reposu yan klasörde: `../Olive_Production_Tracking_App_Back`

## Geliştirme ortamı

```bash
# 1. Laravel ayakta olmalı (Java onun veritabanını ve Docker ağını kullanıyor)
cd ../Olive_Production_Tracking_App_Back/olive_oil_back && docker compose up -d && cd -

# 2. Yerel ayarlar: Laravel'in DB şifresini .env'e yazın (olive_oil_back/.env içindeki DB_PASSWORD)
cp .env.example .env

# 3. Java'yı başlatın. compose.yaml'daki servisler otomatik başlar.
./mvnw spring-boot:run
```

| Adres | Ne |
|---|---|
| http://localhost:8080 | Reverse proxy. Frontend buraya bağlanır. Cevaplardaki `X-Served-By` başlığı hangi sistemin cevap verdiğini gösterir |
| http://localhost:8081 | Java (doğrudan) |
| http://localhost:8001 | Laravel (doğrudan) |
| http://localhost:3000 | Grafana, `admin` / `admin`. "Olive API" dashboard'u |
| http://localhost:9090 | Prometheus |
| http://localhost:8025 | Mailpit |
| localhost:5433 | Ortak Postgres (Laravel'in veritabanına köprü, sadece bu bilgisayardan erişilebilir) |

Java'yı konteynerde çalıştırmak için: `docker compose --profile container up -d --build`
(önce `.env`'de `JAVA_UPSTREAM=api:8081` yapın).

## Testler

```bash
./mvnw test      # birim testleri (*Test, *Tests)
./mvnw verify    # + integration testleri (*IT), Docker gerekir
```

Integration testleri Testcontainers ile Postgres 17 açar ve dondurulmuş referans veriyi
(`reference/reference-data.sql`) yükler.

## Laravel ile karşılaştırma

```bash
# Laravel'in veritabanını dondurulmuş veriye döndür, sonra iki sistemi aynı anda karşılaştır
reference/restore.sh ../Olive_Production_Tracking_App_Back/olive_oil_back
python -I reference/compare.py ../Olive_Production_Tracking_App_Back/olive_oil_back --only api/app
```

## Kod düzeni

```
com.olivamore.api
├── shared/        Ortak altyapı (OPEN modül): Laravel uyumlu hatalar, sayfalama, JSON, silme koruması
└── <modül>/       Faz 2'den itibaren: identity, catalog, inventory, production, ...
```

Laravel uyumluluğu için kurallar:
- **Hatalar:** `BusinessRuleException` (422), `ModelNotFoundException("Order", id)` (404), `LaravelValidationException` (422). Diğer her şey `ApiExceptionHandler`'da Laravel formatına çevrilir.
- **Validation mesajları:** `LaravelMessages` sabitleri, örneğin `@NotBlank(message = LaravelMessages.REQUIRED)`. DTO alanları Laravel'deki kuralların sırasıyla yazılır.
- **Query parametreleri:** Zorunlu olanlar `@Valid @ModelAttribute` bir record'da toplanır, snake_case adlar `@BindParam` ile bağlanır.
- **Listeler:** `Listing.of(...)`. `per_page` yoksa sayfasız, varsa Laravel'in sayfalı formatı.
- **Tarihler:** `Instant` / `LocalDateTime` otomatik olarak `2026-11-06T15:35:14.000000Z` formatında yazılır.
- **Silme:** `DeletionGuard.of("...").usedBy("...", n).check()`
- **Şema:** Geçiş boyunca şemanın sahibi Laravel. Java, Laravel'in tablolarına dokunmaz; sadece kendi altyapı tabloları için migration ekleyebilir (V2: `event_publication`).
