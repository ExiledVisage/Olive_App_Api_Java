# Canlıya Geçiş (Faz 12)

Droplet'teki (`161.35.54.96`) Laravel API'sinin yerine Java API'si geçer. Veritabanı aynı kalır:
veri taşınmaz, Java Laravel'in `db` konteynerine bağlanır. Telefondaki uygulama aynı adrese
(`:8001`) bağlanmaya devam eder, hiçbir şey fark etmez. Java JWT kullandığı için geçişte
herkes yeniden giriş yapar; `JWT_PRIVATE_KEY` tanımlı olmadan uygulama başlamaz.

Evdeki WiFi kurulumu (`main` dalı, SQLite) bu geçişe dahil değil: Java yalnızca PostgreSQL ile çalışır.

## Hazırlık (yerelde yapıldı)

- Laravel'in 196 API route'unun hepsi Java'da var. API dışındaki route'lar (`/`, `/up`,
  `/sanctum/csrf-cookie`, `/storage/{path}`) uygulama tarafından kullanılmıyor.
- Yerel proxy'de her `/api` yolu Java'ya gidiyor, Laravel'e giden istek yok.
- Prova: sunucu compose dosyası (`deploy/compose.server.yaml`) yerelde Laravel'in veritabanına
  bağlanıp ayağa kalktı; 15 ekranın cevabı Laravel'le aynı (rol izinlerinin sırası hariç: Laravel sırasız döndürüyor, DEVIATIONS 11).

## Adımlar

Komutlar sunucuda, `root` olarak. `<...>` yerine gerçek değerler.

### 1. Java reposunu sunucuya al (bir kez)

```bash
cd /srv/olivamore/dev
git clone https://github.com/ExiledVisage/Olive_App_Api_Java.git olive_api_java
cd olive_api_java/deploy
cp .env.server.example .env
# DB_PASSWORD: /srv/olivamore/dev/olive_oil_back/.env içindeki DB_PASSWORD
nano .env
```

### 2. Şema kontrolü

```bash
cd /srv/olivamore/dev/olive_oil_back
docker compose exec -T db sh -c 'pg_dump --schema-only --no-owner --no-privileges -U "$POSTGRES_USER" "$POSTGRES_DB"' > /root/canli-sema.sql
```

Dosyayı bilgisayara al ve karşılaştır (çıktı "Sema ayni." olmalı):

```bash
scp root@161.35.54.96:/root/canli-sema.sql .
deploy/compare-schema.sh canli-sema.sql
```

Fark varsa geçiş durur: sunucudaki Laravel kodu ile Java'nın migration'ları (`db/migration/`) arasında
çalıştırılmamış ya da elle yapılmış bir şema değişikliği vardır, önce o giderilir.

### 3. Yedek

```bash
mkdir -p /srv/olivamore/backups
cd /srv/olivamore/dev/olive_oil_back
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > /srv/olivamore/backups/oncesi-java-$(date +%Y%m%d-%H%M).sql
ls -lh /srv/olivamore/backups
```

### 4. Geçiş

```bash
# Laravel'in API konteyneri durur, veritabanı çalışmaya devam eder. 'down' DEĞİL.
cd /srv/olivamore/dev/olive_oil_back && docker compose stop app

# Java aynı portta açılır (ilk derleme birkaç dakika sürer)
cd /srv/olivamore/dev/olive_api_java/deploy && ./deploy.sh
```

Java ilk açılışta Flyway ile mevcut şemayı kabul eder (`baselineOnMigrate`); Laravel'in
tablolarına dokunmaz, yalnızca kendi iki tablosunu ekler (`flyway_schema_history`,
`event_publication`).

### 5. Kontrol

```bash
curl -s http://localhost:8001/actuator/health                 # {"status":"UP"}
curl -s -X POST http://localhost:8001/api/auth/login -H 'Content-Type: application/json' \
  -H 'Accept: application/json' -d '{"email":"<e-posta>","password":"<şifre>"}'
docker compose -f compose.server.yaml logs -f api             # hata var mı
```

Sonra telefondaki **Olivamore Dev** ile: giriş, parti listesi, bir prosesin detayı, tanklar,
siparişler, günlük özet.

### Geri dönüş (sorun çıkarsa)

```bash
cd /srv/olivamore/dev/olive_api_java/deploy && docker compose -f compose.server.yaml down
cd /srv/olivamore/dev/olive_oil_back && docker compose start app
```

Java'nın veritabanına eklediği iki tablo Laravel'i etkilemez. Java'nın yazdığı veriler de
Laravel'in formatında olduğu için Laravel onları sorunsuz okur.

## Geçişten sonra

Birkaç gün sorunsuz çalıştıktan sonra:

- [ ] Laravel'in `app` konteyneri ve imajı silinir (`docker compose rm app`). Veritabanı konteyneri
      Bölüm B1'e (şema sahipliği) kadar Laravel'in compose'unda kalır
- [ ] Laravel reposu GitHub'da arşivlenir
- [ ] Yerel geçiş düzeni silinir: `compose.laravel.yaml`, `application-laravel.yaml`, `docker/proxy/`,
      `.env.example`'daki geçiş bölümü, README'deki "Geçiş dönemi" bölümü
- [ ] `deploy.sh` Laravel klasöründe değil, Java klasöründe çalıştırılır (ortamlar dokümanı güncellenir)
