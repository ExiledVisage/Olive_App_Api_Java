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

Rol ve izin atamaları yeni tablolara taşınacak. Taşınamayan satır varsa `V5` migration'ı durur, bu yüzden
geçişten önce kontrol edilir. Sunucuda, Laravel klasöründe (bütün satırlar `0` olmalı):

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from (select model_type from model_has_roles union all select model_type from model_has_permissions) m where model_type <> 'App\Models\User';
select count(*) from (select model_id from model_has_roles union all select model_id from model_has_permissions) m where not exists (select 1 from users u where u.id = m.model_id);
select count(*) from (select name from roles group by name having count(*) > 1) d;
select count(*) from (select name from permissions group by name having count(*) > 1) d;
SQL
```

Sıfır olmayan satır varsa geçiş durur ve veri elle düzeltilir: sırasıyla kullanıcı olmayan atama, var olmayan
kullanıcıya ait atama, aynı adı taşıyan rol, aynı adı taşıyan izin.

JSON kolonları `jsonb`'ye çevrilecek. `\u0000` karakteri içeren bir değer varsa `V6` durur, bu yüzden o da
kontrol edilir (bütün satırlar `0` olmalı):

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from batch_processes where operation_data::text like '%\u0000%';
select count(*) from batch_process_reviews where operation_data::text like '%\u0000%';
select count(*) from process_type_fields where options::text like '%\u0000%';
select count(*) from batch_process_field_values where value_json::text like '%\u0000%';
select count(*) from stage_extension_type_fields where default_value_json::text like '%\u0000%';
select count(*) from stage_extension_type_fields where options_json::text like '%\u0000%';
select count(*) from production_stage_extension_values where value_json::text like '%\u0000%';
select count(*) from material_attribute_values where value_json::text like '%\u0000%';
select count(*) from analytics_reports where config::text like '%\u0000%';
SQL
```

Yoğunluğu bilinmeyen ürün tanka giremez; tanklarda yoğunluğu tanımsız ürün tipinden aktif materyal kalmamalı
(sonuç `0` olmalı). Sıfır değilse geçişten önce o ürün tiplerine ya da ürünlerine yoğunluk (kg/L) girilir:

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from materials m
left join production_product_types t on t.id = m.production_product_type_id
left join production_products p on p.id = t.production_product_id
where m.current_location_type = 'storage_unit' and m.status = 'active'
and coalesce(case when t.density_kg_per_litre > 0 then t.density_kg_per_litre end,
case when p.default_density_kg_per_litre > 0 then p.default_density_kg_per_litre end) is null;
SQL
```

Dinlenme tankları canlıda yalnızca metinle belirtiliyor. `V7`, kodunda, adında ya da açıklamasında "dinlen" geçen
tankları ön dinlendirme tankı yapar, diğerleri son tank olur. Hangi tankların seçileceği önceden listelenir:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select id, code, name, description from storage_units
where concat_ws(' ', code, name, description) ~* 'd[iıİI]nlen' order by id;
SQL
```

Liste fabrikadaki dinlenme tanklarıyla aynı olmalı. Yanlış eşleşen tankın metni düzeltilir, eksik kalan dinlenme
tankının adına "dinlenme" eklenir. Son tanka giren parti materyali kapanır, yanlış türde kalan dinlenme tankı
üretimdeki toplama aşamasını durdurur.

Dinlenme tanklarında bekleyen materyaller yeni ön dinlendirme aşamasıyla girmediği için Boşalt'ta tortu
hesaplanmadan kapanır (pirinaları zaten elle girildi). Sayıları önceden bilinir:

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from materials m
join storage_units u on u.id = m.current_location_id
where m.current_location_type = 'storage_unit' and m.status = 'active'
and concat_ws(' ', u.code, u.name, u.description) ~* 'd[iıİI]nlen';
SQL
```

Java'da dinlenme tankından elle çıkış ya da transfer yapılamaz, çıkış yalnızca Boşalt ile. Dinlenme tankına giren ya
da oradan çıkan depo hareketi uzantılı aşamalar (eski "ön dinlenme" ve "tanklarda toplama") geçişten sonra çalışmaz;
listelenir ve geçişte yeni `pre_settling` uzantısına çevrilir ya da uzantısı kaldırılır:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select l.name as hat, s.stage_order, s.name as asama, v.value_text as hareket
from production_stage_extensions e
join stage_extension_types t on t.id = e.stage_extension_type_id and t.key = 'storage_movement'
join production_stages s on s.id = e.production_stage_id
join production_lines l on l.id = s.production_line_id
left join production_stage_extension_values v on v.production_stage_extension_id = e.id
order by l.id, s.stage_order;
SQL
```

Son tanklarda (dinlenme tankları dışındakiler) aynı ürün tipinin birden çok aktif materyali durabilir: parti başına
parçalar, açılışlar. `V10` bunları tank ve tip başına tek tank materyalinde birleştirir (miktar tipin birimine
çevrilip toplanır, sayısal özellikler miktara göre ortalanır), eskileri kapatır. Kaç grubun birleşeceği ve kaç
materyalin kapanacağı bilgi için görülür:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select u.code as tank, t.name as tip, count(*) as materyal, string_agg(distinct m.unit, ', ') as birim
from materials m
join storage_units u on u.id = m.current_location_id
join production_product_types t on t.id = m.production_product_type_id
where m.current_location_type = 'storage_unit' and m.status = 'active'
and concat_ws(' ', u.code, u.name, u.description) !~* 'd[iıİI]nlen'
group by u.code, t.name order by u.code, t.name;
SQL
```

Aynı tipte farklı birimler (KG ve LITRE) varsa çevrim için yoğunluk gerekir; yoksa `V10` durur. Sonuç `0` olmalı,
değilse yukarıdaki yoğunluk adımındaki gibi tipe ya da ürüne yoğunluk girilir:

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from (
select m.current_location_id, m.production_product_type_id from materials m
join storage_units u on u.id = m.current_location_id
join production_product_types t on t.id = m.production_product_type_id
left join production_products p on p.id = t.production_product_id
where m.current_location_type = 'storage_unit' and m.status = 'active'
and concat_ws(' ', u.code, u.name, u.description) !~* 'd[iıİI]nlen'
and coalesce(case when t.density_kg_per_litre > 0 then t.density_kg_per_litre end,
case when p.default_density_kg_per_litre > 0 then p.default_density_kg_per_litre end) is null
group by m.current_location_id, m.production_product_type_id
having count(distinct m.unit) > 1 or bool_or(m.unit <> t.unit)) g;
SQL
```

Zorunlu özelliği girilmemiş materyaller de birleşir (zaten tanktalar); eksik değer karışımda bilinmez olur, geçişten
sonra tank ölçümüyle girilir. Liste bilgi içindir:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select u.code as tank, m.id as materyal, a.label as eksik
from materials m
join storage_units u on u.id = m.current_location_id
join production_product_types t on t.id = m.production_product_type_id
join production_product_attribute_definitions d on d.production_product_id = t.production_product_id and d.is_required
join attribute_definitions a on a.id = d.attribute_definition_id
where m.current_location_type = 'storage_unit' and m.status = 'active'
and concat_ws(' ', u.code, u.name, u.description) !~* 'd[iıİI]nlen'
and not exists (select 1 from material_attribute_values v where v.material_id = m.id
and v.production_product_attribute_definition_id = d.id)
order by u.code, m.id, a.label;
SQL
```

Planlanmış ve devam eden dolumların eski materyallere ayırdığı miktarlar tank materyaline taşınır; sayı bilgi
içindir:

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(distinct r.id) from finished_product_runs r
join finished_product_run_materials rm on rm.finished_product_run_id = r.id
join materials m on m.id = rm.source_material_id
join storage_units u on u.id = m.current_location_id
where r.status in ('planned', 'in_progress') and m.current_location_type = 'storage_unit' and m.status = 'active'
and concat_ws(' ', u.code, u.name, u.description) !~* 'd[iıİI]nlen';
SQL
```

Onay bekleyen depo hareketi uzantılı işlem kalmamalı (sonuç `0` olmalı); varsa geçişten önce Laravel'de onaylanır ya
da reddedilir. Birleşecek bir materyali taşıyan bekleyen çıkış ya da transfer kalırsa `V10` durur:

```bash
docker compose exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select count(*) from batch_processes bp
join production_stage_extensions e on e.production_stage_id = bp.production_stage_id and e.is_enabled
join stage_extension_types t on t.id = e.stage_extension_type_id and t.key = 'storage_movement'
where bp.status = 'awaiting_approval';
SQL
```

Depo hareketi uzantılı aşamaların sabit hareket türü ve hedef tankın türü birlikte görülür. Son tankta artık parti
materyali kalmadığı için üretimden `outbound` ve `transfer` çalışmaz (yalnızca tanka giriş çalışır); dinlenme tankını
hedefleyen aşama `pre_settling`'e çevrilir:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
select l.name as hat, s.stage_order, s.name as asama,
coalesce(nullif(trim(v.value_text), ''), 'sabit değil') as hareket, string_agg(distinct case when concat_ws(' ', u.code, u.name, u.description) ~* 'd[iıİI]nlen'
then 'dinlenme' else 'son tank' end || ' ' || u.code, ', ') as hedef_tanklar
from production_stage_extensions e
join stage_extension_types t on t.id = e.stage_extension_type_id and t.key = 'storage_movement'
join production_stages s on s.id = e.production_stage_id
join production_lines l on l.id = s.production_line_id
left join production_stage_extension_values v on v.production_stage_extension_id = e.id
and v.stage_extension_type_field_id in (select id from stage_extension_type_fields where field_key = 'action')
left join batch_processes bp on bp.production_stage_id = s.id
left join storage_movements sm on sm.batch_process_id = bp.id
left join storage_units u on u.id = sm.storage_unit_id
group by l.id, l.name, s.stage_order, s.name, v.value_text
order by l.id, s.stage_order;
SQL
```

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

Java ilk açılışta Flyway ile mevcut şemayı kabul eder (`baselineOnMigrate`), sonra kendi migration'larını
uygular. **Bu migration'lar Laravel'in tablolarını değiştirir:**

- `V2`, `V3`: `event_publication`, `auth_sessions` ve `refresh_tokens` eklenir, `personal_access_tokens` silinir.
- `V4`: `migrations`, `cache`, `cache_locks`, `jobs`, `job_batches`, `failed_jobs`, `sessions` ve
  `password_reset_tokens` silinir.
- `V5`: rol ve izin atamaları `user_roles`, `user_permissions`, `role_permissions` tablolarına aynen taşınır,
  Spatie tabloları ile `guard_name` ve `remember_token` kolonları silinir.
- `V6`: JSON kolonları `jsonb`'ye çevrilir. Tablolar yeniden yazılır, uygulama durmuşken çalışır; veri korunur.
- `V7`: `storage_units`'a tank türü (`kind`) eklenir; metninde "dinlen" geçen tanklar ön dinlendirme
  (`pre_settling`), diğerleri son tank (`final`) olur. Ön dinlendirme tanklarında blend ve çoklu ürün/tip ayarları
  kapatılır (tek ürün tipiyle çalışır).
- `V8`: `storage_movements`'a gelen materyal (`source_material_id`) ve son tank ölçüm geçmişi tablosu
  (`tank_measurements`) eklenir. Var olan satırlara dokunulmaz.
- `V9`: `stage_extension_types`'a ön dinlendirme uzantısı (`pre_settling`) eklenir. Parti aşaması yeni bir durum
  alabilir: `resting` (dinlenmede, tank boşaltılınca tamamlanır); kolon serbest metin olduğu için şema değişmez.
- `V10`: son tanklardaki aktif materyaller tank ve ürün tipi başına tek tank materyalinde (`origin_type = 'TANK'`)
  birleşir; her eski materyal için bir `TRANSFER_IN` (`BIRLESTIRME`) yazılır, eskiler kapanır. Etkilenen tankların
  bakiyeleri yeniden hesaplanır, açık dolumların ayırdığı miktarlar tank materyaline taşınır. Yoğunluk eksikse ya da
  birleşecek materyali taşıyan onay bekleyen çıkış/transfer varsa durur (2. adımdaki kontroller).

Bu andan sonra Laravel aynı veritabanıyla çalışamaz.

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

Java'nın migration'ları şemayı değiştirdiği için geri dönüş, 3. adımda alınan yedeğin geri yüklenmesidir.
**Yedekten sonra Java üzerinden yazılan her veri kaybolur.** Bu yüzden karar, geçişten sonra olabildiğince
erken verilmelidir.

```bash
cd /srv/olivamore/dev/olive_api_java/deploy && docker compose -f compose.server.yaml down
cd /srv/olivamore/dev/olive_oil_back

# Veritabanı boşaltılıp yedek yüklenir; <tarih> yerine 3. adımdaki dosyanın adı
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE \"$POSTGRES_DB\" WITH (FORCE);" -c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\";"'
docker compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /srv/olivamore/backups/oncesi-java-<tarih>.sql

docker compose start app
```

`V5` hata verip uygulama açılmazsa da aynı yol izlenir: önceki migration'lar uygulanmış olabilir.

## Geçişten sonra

Birkaç gün sorunsuz çalıştıktan sonra:

- [ ] Laravel'in `app` konteyneri ve imajı silinir (`docker compose rm app`). Veritabanı konteyneri
      Bölüm B1'e (şema sahipliği) kadar Laravel'in compose'unda kalır
- [ ] Laravel reposu GitHub'da arşivlenir
- [ ] Yerel geçiş düzeni silinir: `compose.laravel.yaml`, `application-laravel.yaml`, `docker/proxy/`,
      `.env.example`'daki geçiş bölümü, README'deki "Geçiş dönemi" bölümü
- [ ] `deploy.sh` Laravel klasöründe değil, Java klasöründe çalıştırılır (ortamlar dokümanı güncellenir)
