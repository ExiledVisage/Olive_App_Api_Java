# Tech Stack

## Core
- Java 25 (virtual threads)
- Spring Boot 4
- Maven (mvnw)

## Web
- Spring MVC (sunucu, servlet)
- RestClient: basit istek-cevap dış çağrılar
- SSE (MVC `SseEmitter`): tek yönlü canlı veri
- Jackson 3
- Validation

## Mimari
- Spring Modulith (event'li modüller + Event Publication Registry)

## Güvenlik
- Spring Security
- OAuth2 Resource Server (JWT, NimbusJwtEncoder, RSA)
- Refresh token rotation (DB'de hash'li)

## Veri
- PostgreSQL
- Spring Data JPA / Hibernate: yazma tarafı, domain işlemleri
- jOOQ: analitik, raporlar ve karmaşık okuma sorguları
- Hibernate JSON mapping + Postgres JSONB: dinamik alanlar (attribute definition, process type field vb.)
- Flyway

## Kod üretimi
- Lombok
- MapStruct (+ lombok-mapstruct-binding)

## Entegrasyonlar
- Mail: Spring Mail + Thymeleaf, notification modülünde async (dev: Mailpit)

## Test
- JUnit 5 · Mockito · AssertJ
- Testcontainers (@ServiceConnection)
- Spring Modulith tests (verify + @ApplicationModuleTest)
- Surefire (*Test) / Failsafe (*IT)

## Operasyon
- Spring Boot Actuator
- Micrometer + Prometheus: uygulama metriklerini toplar (istek süreleri, hata oranları, JVM, DB bağlantı havuzu)
- Grafana: Prometheus metrikleri için dashboard ve alarm. Ayrıca doğrudan Postgres'e bağlanıp iş verisi dashboard'ları da gösterebilir
- Docker + Docker Compose: uygulama, Postgres, Mailpit, Prometheus, Grafana
- spring-boot-docker-compose: geliştirme ortamında compose servislerini uygulama açılırken otomatik başlatır

---

## Gelecekteki İhtimaller

Şu an kullanılmıyor, ama ileride eklenebilir.

### Mobil Push Bildirimleri
- **Firebase Cloud Messaging (FCM):** Android ve iOS'a tek API üzerinden bildirim gönderir. iOS tarafında APNs'i arka planda FCM yönetir.
- Notification modülünün içinde yer alır. Kullanıcı cihaz token'ları (device token) DB'de tutulur.
- Gönderim Modulith event'leriyle async yapılır. Aynı event uygulama içi bildirim, mail ve push için ortak kullanılır.
- **Ek kanal olarak SMS:** TR için Netgsm / İleti Merkezi, global için Twilio.

### Ödeme
- **Stripe** (şirket TR'deyse **iyzico**), `PaymentGateway` arayüzünün arkasında.
- Webhook ile ödeme sonucu alınır. Idempotency key ile tekrar eden istekler engellenir.

### Yapay Zeka / Copilot
- **Spring AI:** LLM sağlayıcılarına (Claude, OpenAI vb.) ortak bir API sağlar.
- **WebFlux (sadece WebClient + Reactor):** LLM cevaplarını streaming almak ve SSE ile frontend'e iletmek için.
- **pgvector:** Doküman ya da veri üzerinde anlamsal arama (RAG) gerekirse, ayrı bir vektör veritabanına gerek kalmadan Postgres içinde çalışır.

### Gerçek Zamanlı İletişim
- **Spring WebSocket (STOMP):** Yalnızca çift yönlü gerçek zamanlı ihtiyaç olursa. Tek yönlü canlı veri için SSE yeterli.

---

## Yakın Alternatifler

Seçilen teknolojilerin yerine ya da yanında kullanılabilecek seçenekler.

| Alternatif | Yerine / Yanına | Neden kullanılabilir |
|---|---|---|
| **JdbcClient** | jOOQ | Spring'in içinde gelir, ek bağımlılık ve kod üretimi gerektirmez. Az sayıda basit rapor sorgusu için yeterli. |
| **Java Record** | Lombok (DTO'larda) | Immutable DTO'lar için dilin kendi özelliği, Lombok'a gerek bırakmaz. MapStruct record'ları destekler. |
| **Liquibase** | Flyway | DB'den bağımsız (XML/YAML) migration ve rollback desteği. Tek DB (Postgres) kullanılacağı için Flyway daha sade kalır. |
| **Keycloak / Spring Authorization Server** | Kendi JWT üretimi | SSO, sosyal giriş ya da birden çok uygulamanın ortak kimlik doğrulaması gerekirse. |
| **springdoc-openapi** | (yanına) | Swagger UI ve OpenAPI dokümanı üretir. Ayrı bir frontend olduğu için API sözleşmesini netleştirir. |
| **ShedLock** | (`@Scheduled`'in yanına) | Birden fazla instance çalışırken zamanlanmış işlerin (günlük özet vb.) tek bir instance'ta çalışmasını garanti eder. |
| **MinIO / S3** | Lokal disk | Dosya depolama (AppRelease, APK vb.). Ölçeklenebilir ve instance'tan bağımsız. |
| **Redis** | (yanına) | Cache, rate limiting ya da token blacklist gerekirse. |
| **RabbitMQ / Kafka** | (Modulith event'lerinin yanına) | Modulith event externalization ile event'ler dış servislere taşınabilir. Modüller ayrı servislere bölünürse kullanılır. |
