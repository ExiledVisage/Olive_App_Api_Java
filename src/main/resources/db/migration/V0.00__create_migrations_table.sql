-- Laravel'in migration kayit tablosu (php artisan migrate:install).
--
-- V0.xx dosyalari Laravel'in migration'larinin birebir SQL'idir, tablo tablo ve ayni sirada. SQL
-- Laravel'in kendisine uretildi (php artisan migrate --pretend, PostgreSQL); yalnizca sema
-- komutlari alindi. Veri ekleyen ya da duzelten komutlar (izinler, eski kayitlarin doldurulmasi)
-- burada yok, veri --seed ile gelir.
--
-- Laravel'in olusturdugu mevcut bir veritabaninda bu dosyalar CALISMAZ: Flyway baseline-on-migrate
-- ile semayi surum 1 kabul eder, V0.xx'ler bunun altinda kalir. Yalnizca bos veritabaninda
-- (testler, yeni kurulum) calisirlar; sonucun Laravel'in semasiyla ayni oldugunu BaselineSchemaIT
-- dogrular.

create table "migrations" (
    "id" serial primary key not null,
    "migration" varchar(255) not null,
    "batch" integer not null
);
