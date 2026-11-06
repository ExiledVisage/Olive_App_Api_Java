#!/usr/bin/env bash
#
# Laravel'in dev veritabanini dondurulmus referans veriye (reference-data.sql) geri dondurur.
# Karsilastirma ve yakalama oncesi her seferinde calistirilir.
#
#   reference/restore.sh <laravel_compose_dir>
#
# Ornek:
#   reference/restore.sh ../Olive_Production_Tracking_App_Back/olive_oil_back
#
# DIKKAT: hedef veritabanindaki HER SEYI siler. Yalnizca dev ortaminda calisir.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
compose_dir="${1:?Laravel compose klasoru verilmeli}"
cd "$compose_dir"

proje="$(grep -E '^COMPOSE_PROJECT_NAME=' .env | cut -d= -f2- | tr -d '"'\'' \r')"
case "$proje" in
    *dev*) ;;
    *) echo "HATA: '$proje' dev ortami degil, durduruldu." >&2; exit 1 ;;
esac

echo "==> '$proje' veritabani sifirlaniyor"
docker compose exec -T db sh -c 'PGOPTIONS="-c client_min_messages=warning" psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'

echo "==> Referans veri yukleniyor"
docker compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$here/reference-data.sql" > /dev/null

# Laravel'in cache tablosunda eski izin cache'i kalmasin
docker compose exec -T app php artisan cache:clear > /dev/null

echo "==> Hazir: $(docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select count(*) from production_batches"' | tr -d ' \r') uretim partisi"
