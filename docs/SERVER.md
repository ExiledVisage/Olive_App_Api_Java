# Sunucu

Geliştirme sunucusu bir DigitalOcean droplet'i (`161.35.54.96`, Ubuntu 24.04, 1 CPU, 1 GB RAM). API ve
PostgreSQL aynı makinede, Docker'da çalışır (`deploy/compose.server.yaml`). API `8001` portunda açıktır,
veritabanı dışarıya açılmaz. Verisi demo verisidir.

## İlk kurulum

Komutlar sunucuda, `root` olarak.

```bash
# 1 GB RAM için swap (derleme ve seed bellek ister)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Docker: https://docs.docker.com/engine/install/ubuntu/ (docker-ce ve docker-compose-plugin)

mkdir -p /srv/olivamore/dev && cd /srv/olivamore/dev
git clone https://github.com/ExiledVisage/Olive_App_Api_Java.git olive_api_java
cd olive_api_java/deploy
cp .env.server.example .env
```

`.env`'de doldurulacaklar:

- `DB_PASSWORD`: rastgele bir değer, örn. `openssl rand -hex 24`. Veritabanı konteyneri ilk açılışta bu şifreyle
  kurulur.
- `JWT_PRIVATE_KEY`: imza anahtarı, tek satır, satır sonları `\n`:
  ```bash
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /root/jwt.pem
  awk '{printf "%s\\n", $0}' /root/jwt.pem
  ```
  Anahtar değişirse herkes yeniden giriş yapar.

Sonra API derlenip başlatılır ve demo veri bir kez yüklenir. Bellek az olduğu için seed sırasında API durdurulur:

```bash
./deploy.sh
docker compose -f compose.server.yaml stop api
docker compose -f compose.server.yaml run --rm --no-deps api --seed    # bugün biten bir yıl, ~10 dakika
docker compose -f compose.server.yaml start api
```

## Güncelleme

```bash
cd /srv/olivamore/dev/olive_api_java/deploy && ./deploy.sh
```

`main` dalını çeker, imajı derler, API'yi yeniden başlatır ve sağlık kontrolünü bekler. Yeni migration'lar açılışta
Flyway ile uygulanır.

## Sıfırlama

Veritabanı (volume) silinir, şema ve demo veri baştan kurulur:

```bash
cd /srv/olivamore/dev/olive_api_java/deploy
docker compose -f compose.server.yaml down -v
./deploy.sh
docker compose -f compose.server.yaml stop api
docker compose -f compose.server.yaml run --rm --no-deps api --seed
docker compose -f compose.server.yaml start api
```

## Kontrol

```bash
curl -s http://161.35.54.96:8001/actuator/health          # {"status":"UP"}
docker compose -f compose.server.yaml logs -f api           # sunucuda, kayıtlar
```

Demo kullanıcılar: `admin@olivamore.com` ve diğerleri, şifreleri `123`.
