#!/usr/bin/env bash
#
# Canli veritabaninin semasini Java'nin migration'lariyla (db/migration/V0.xx: tablo tablo, 69
# migration'i) kurulan semayla karsilastirir (Faz 12, gecisten once).
# Veriye bakmaz. Iki sema gecici bir PostgreSQL 17 konteynerine yuklenir ve ayni pg_dump ile
# yeniden dokulur; boylece bicim farklari karsilastirmaya girmez.
#
#   1) Sunucuda (Laravel klasorunde), yalnizca sema:
#        docker compose exec -T db sh -c 'pg_dump --schema-only --no-owner --no-privileges -U "$POSTGRES_USER" "$POSTGRES_DB"' > canli-sema.sql
#   2) Bu bilgisayarda:
#        deploy/compare-schema.sh canli-sema.sql
#
# Cikti bossa sema ayni. Java'nin kendi tablolari (flyway_schema_history, event_publication) ve
# 'public' semasinin aciklamasi (sema yeniden olusturulunca degisir) karsilastirmaya girmez.
# IN listeli check kisitlarinin iki yazilisi (migration'la kurulan / dump'tan yuklenen) ayni sayilir.
set -euo pipefail

live="$(cd "$(dirname "${1:?canli sema dosyasi verilmeli}")" && pwd)/$(basename "$1")"
here="$(cd "$(dirname "$0")" && pwd)"
migrations="$here/../src/main/resources/db/migration"
name="olive-schema-compare-$$"

docker run -d --rm --name "$name" -e POSTGRES_PASSWORD=x postgres:17-alpine > /dev/null
trap 'docker stop "$name" > /dev/null' EXIT
until docker exec "$name" pg_isready -U postgres -q 2>/dev/null; do sleep 1; done
sleep 2

load() {
    docker exec "$name" createdb -U postgres "$1"
    # \restrict satirlari pg_dump 17.6+ ile gelir; eski psql'lerde sorun cikarmasin
    grep -v '^\\\(un\)\?restrict' "$2" | docker exec -i "$name" psql -q -v ON_ERROR_STOP=1 -U postgres -d "$1" > /dev/null
}
dump() {
    docker exec "$name" pg_dump --schema-only --no-owner --no-privileges -U postgres \
        -T flyway_schema_history -T event_publication "$1" \
        | grep -v '^\\\(un\)\?restrict\|^--\|^COMMENT ON SCHEMA public' | cat -s \
        | sed -E "s/\('([^']*)'::character varying\)::text/'\1'::character varying/g; s/ANY \(ARRAY\[([^]]*)\]\)/ANY ((ARRAY[\1])::text[])/g"
}

load live "$live"
# Laravel migration'lari surum sirasiyla (V0.00 ... V0.69)
docker exec "$name" createdb -U postgres baseline
for f in $(ls "$migrations"/V0.*.sql | sort); do
    docker exec -i "$name" psql -q -v ON_ERROR_STOP=1 -U postgres -d baseline < "$f" > /dev/null 2>&1 \
        || { echo "HATA: $f calismadi" >&2; exit 1; }
done

if diff <(dump baseline) <(dump live); then
    echo "==> Sema ayni."
else
    echo "==> FARK VAR (< baseline, > canli). Gecisten once giderilmeli." >&2
    exit 1
fi
