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
- [ ] Spring Boot 4 projesi, Maven wrapper, Modulith iskeleti
- [ ] Docker Compose: Java uygulaması, Laravel, Postgres, Mailpit, reverse proxy
- [ ] **Flyway baseline:** Faz 0'daki temiz DB'den şema `pg_dump --schema-only` ile `V1__baseline.sql` olarak alınır, `baselineOnMigrate` açılır. Hazır döküm: `reference/schema.sql` (pg_dump 17'nin eklediği `\restrict` / `\unrestrict` satırları Flyway'e verilmeden önce silinmeli). Geçiş bitene kadar şema değişiklikleri **sadece Laravel migration'larıyla** yapılır, Java tarafı okuyup uyum sağlar
- [ ] Jackson: snake_case, Laravel ile aynı tarih formatı
- [ ] Ortak pagination cevabı: `per_page` yoksa sayfasız `{"data": [...]}`, `per_page` (1–200) varsa Laravel `paginate()` çıktısının birebir aynısı (`data`, `links`, `meta`)
- [ ] Global hata yönetimi: `BusinessRuleException`, validation (422), 404, 403 cevapları Laravel formatında
- [ ] Silme koruması (`GuardsDeletion` karşılığı)
- [ ] **Test altyapısı:** Testcontainers, dondurulmuş referans verinin test DB'sine yüklenmesi, Laravel/Java cevap karşılaştırma aracı, ilk `*IT` testi
- [ ] **Gözlemlenebilirlik (en basit haliyle):** Actuator, Micrometer, Prometheus, Grafana. Her modül proxy'de Java'ya çevrildikten sonra hata oranı ve yanıt süresini izlemek için. Detaylı dashboard'lar Geçiş Sonrası'nda

## Faz 2: Kimlik ve Yetki (identity)

Diğer bütün modüller buna bağlı olduğu için ilk taşınacak modül budur.
- [ ] `users`, Spatie tabloları (`roles`, `permissions`, `model_has_roles`, `model_has_permissions`, `role_has_permissions`) için JPA entity'ler
- [ ] **Sanctum uyumlu auth:** Java, Laravel'in Sanctum token'larını doğrular. Sanctum token'ı `personal_access_tokens` tablosunda SHA-256 hash olarak durur, Java aynı tabloya bakar. Böylece kullanıcı hangi sistemden giriş yaptıysa diğerinde de geçerli olur
- [ ] **Token doğrulaması tek bir yerden değiştirilebilir olmalı.** Geçiş Sonrası'nda Sanctum yerine JWT konulacağı için doğrulama mantığı bir arayüzün arkasında durur
- [ ] Spatie izinlerinin Spring Security authority'lerine eşlenmesi (`permission:x` → `hasAuthority("x")`, `role:admin` → `hasRole("admin")`)
- [ ] Login/register throttle (`AuthThrottleTest`)
- [ ] User, Role, Permission, UserRole endpoint'leri
- Testler: `PermissionTest`, `AuthThrottleTest`

## Faz 3: Bağımsız Küçük Uç (app-release)

Pipeline'ı uçtan uca denemek için en risksiz modül: DB'ye dokunmuyor, auth gerektirmiyor.
- [ ] `/api/app/version`, `/api/app/download`
- [ ] Proxy'de ilk yönlendirme bununla denenir
- Test: `AppReleaseTest`

## Faz 4: Üretim Altyapısı ve Ana Veriler (catalog)

Henüz hareket yok, sadece tanımlar var. Sonraki her modül buna bağlı.
- [ ] Production product type, production product, attribute definitions. Dinamik alan tabloları **mevcut yapısıyla birebir** taşınır (JSONB'ye geçiş Geçiş Sonrası'nda)
- [ ] `UnitConverter` (birim dönüşümleri, yoğunluk)
- [ ] Process type, process type field
- [ ] Stage extension type ve alanları
- [ ] Production line (giriş/çıkış ürün tipleri), production stage, stage extension
- [ ] Supplier, supplier field, product supplier
- Testler: `UnitConversionTest`, `ProductionLineProductTypeTest`, `StageExtensionTest`, `SupplierTest`, `DeletionGuardTest`

## Faz 5: Depo ve Malzeme (inventory)

**En kritik faz.** Stok defteri buradan yönetiliyor, en büyük servis (StorageMovement) de burada.
- [ ] Storage unit, izinli ürün/tip kuralları, attribute kuralları
- [ ] Material, material attribute value
- [ ] Storage movement (giriş, transfer, düzeltme) ve storage unit balance
- [ ] Material lineage (malzeme soy ağacı)
- [ ] Eşzamanlılık: bakiye güncellemelerinde `SELECT ... FOR UPDATE` ya da `@Version`
- Testler: `StorageUnitTest`, `MaterialStorageTest`, `InboundMovementTest`, `MaterialListTest`, `MaterialDetailTest`, `MaterialLineageTest`

## Faz 6: Üretim Partileri (production)

Faz 4 ve 5'e bağlı: prosesler malzeme üretir ve depoya hareket yazar.
- [ ] Production batch (giriş/çıkış ürün tipleri, takvim)
- [ ] Batch process ve alt tipleri: tartım (Weighing), dönüşüm (MaterialTransformation), depo hareketi (StorageMovement), analiz (Analysis)
- [ ] Batch process field values, reviews (onay akışı)
- [ ] Stage extension values
- Testler: `ProductionTest`, `ProductionFlowTest`, `ProductionBatchCalendarTest`

## Faz 7: Ambalaj (packaging)

- [ ] Packaging item
- [ ] Packaging lot, lot movement (kabul, düzeltme)
- Test: `PackagingLotTest`

## Faz 8: Bitmiş Ürün (finished-product)

Faz 5, 6 ve 7'ye bağlı: dolum koşusu tanktan yağ, depodan ambalaj tüketiyor.
- [ ] Finished product recipe (içerik, attribute kuralları)
- [ ] Finished product, components
- [ ] Finished product run (malzeme tüketimi, ambalaj tüketimi, attribute values)
- [ ] Finished product storage, slot, slot stock, stock movements
- Testler: `FinishedProductRunTest`, `FinishedProductStorageTest`

## Faz 9: Müşteri ve Sipariş (sales)

Faz 8'e bağlı: sipariş hazırlanırken şişeler raftan düşüyor.
- [ ] Customer, customer address, preferred recipes
- [ ] Order, order item, status history
- [ ] Sipariş hazırlama (pick): raftan stok düşümü **aynı transaction'da ve senkron**
- [ ] Sevkiyat bekleyen adet hesabı (defterden türetilir)
- Testler: `CustomerRecipeTest`, `OrderTest`, `OrderPickTest`

## Faz 10: Bildirim (notification)

- [ ] User notifications, bildirim gönderme endpoint'leri
- [ ] Diğer modüllerin olaylarına Modulith event listener'ları (`@ApplicationModuleListener`)
- [ ] Mail: Spring Mail + Thymeleaf
- Test: `NotificationTest`

## Faz 11: Analitik ve Günlük Özet (analytics)

Bütün modüllerin verisini okuduğu için en son taşınır. Hiçbir modül buna bağlı değil.
- [ ] jOOQ kod üretimi (mevcut şemadan)
- [ ] Analytics service, kayıtlı raporlar (analytics reports)
- [ ] Daily summary
- Testler: `AnalyticsTest`, `AnalyticsReportTest`, `DailySummaryTest`

## Faz 12: Canlıya Geçiş ve Laravel'in Kapatılması

Canlıya geçiş modül modül de yapılabilir (her modül dev'de bitince canlıda da açılır) ya da tüm modüller dev'de bitince bir kerede. Her iki durumda da:
- [ ] **Şema kontrolü:** Sunucudaki canlı şema (`pg_dump --schema-only`) `V1__baseline.sql` ile karşılaştırılır. Elle yapılmış bir değişiklik ya da çalıştırılmamış bir migration varsa önce o giderilir. Bunun için veriye değil, sadece şemaya bakılır
- [ ] **Her geçişten önce canlı veritabanının yedeği alınır**
- [ ] Java canlı veritabanına bağlanır. Flyway `baselineOnMigrate` ile mevcut şemayı kabul eder, hiçbir tabloya dokunmaz
- [ ] Canlıda proxy yönlendirmeleri açılır, Grafana'dan izlenir
- [ ] Tüm trafik Java'da, Laravel'e giden istek yok (proxy log'larıyla doğrulanır)
- [ ] Laravel konteyneri kapatılır, repo arşivlenir

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
- [ ] `OliveOilProductionFlowSeeder` ve demo fabrika akışının Java karşılığı
- [ ] `seed-demo.sh` karşılığı (sadece dev ortamında çalışacak koruma ile)

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
- **Geçiş süresince yeni özellik yok.** Laravel'e yeni özellik eklenirse Java'ya da eklenmesi gerekir. Zorunlu bir değişiklik olursa önce taşınmış tarafta yapılır.
- **Şemanın sahibi geçiş boyunca Laravel'dir.** İki tarafta migration yazılmaz.
- **İş mantığı yeniden tasarlanmaz, birebir taşınır.** İyileştirmeler Bölüm B'de ya da sonrasında yapılır. Aksi halde bir hatanın taşımadan mı yoksa yeni tasarımdan mı geldiği anlaşılmaz.
- **Laravel `dev` branch'ine yeni commit gelirse** ilgili fazlar `ba61990` sonrasındaki farka bakılarak güncellenir.
- **Her modülün workflow dokümanı** (`docs/workflow-*.md`) o fazın şartnamesi gibi kullanılır.
