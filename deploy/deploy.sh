#!/usr/bin/env bash
#
# Sunucuda tek komutla guncelleme (Laravel'in deploy.sh'i ile ayni duzen).
#
#   cd /srv/olivamore/dev/olive_api_java/deploy && ./deploy.sh
#
# Bulundugu klasorun git dalini ceker, imaji derler, API'yi baslatir ve saglik kontrolu
# gecene kadar bekler. Gecmezse kayitlari basip hata dondurur.
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo "HATA: .env yok. '.env.server.example' kopyalanip doldurulmali." >&2
    exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
before="$(git rev-parse --short HEAD)"
echo "==> Ortam: $(grep -E '^COMPOSE_PROJECT_NAME=' .env | cut -d= -f2-) | dal: $branch"

echo "==> Kod cekiliyor"
# --ff-only: sunucu yalnizca uzaktaki dalin aynasi; yerel degisiklik varsa hata versin
git pull --ff-only origin "$branch"

after="$(git rev-parse --short HEAD)"
if [ "$before" = "$after" ]; then
    echo "    Zaten guncel ($after), yine de yeniden kuruluyor."
else
    echo "    $before -> $after"
fi

echo "==> Imaj derleniyor, API baslatiliyor"
docker compose -f compose.server.yaml up -d --build

echo "==> API bekleniyor"
for _ in $(seq 1 60); do
    if docker compose -f compose.server.yaml exec -T api wget -qO- http://localhost:8081/actuator/health 2>/dev/null | grep -q '"UP"'; then
        echo "==> Hazir."
        docker compose -f compose.server.yaml ps
        exit 0
    fi
    sleep 2
done

echo "HATA: API 120 saniyede ayaga kalkmadi. Son kayitlar:" >&2
docker compose -f compose.server.yaml logs --tail=80 api >&2
exit 1
