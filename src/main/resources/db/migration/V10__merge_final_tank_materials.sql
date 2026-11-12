-- Son tank modeli (C2) eski veriye uygulanir: son tanklarda (kind = 'final') tank ve urun tipi basina duran aktif
-- materyaller (parti parcalari, acilislar) tek tank materyalinde (origin_type = 'TANK', partisiz) birlesir. Calisma
-- anindaki giris kurali ayni: miktar tipin birimine cevrilip (4 basamak) toplanir; sayisal attribute'lar miktara
-- gore agirlikli ortalama, digerleri hepsinde ayniysa kalir; bir materyalde bile olmayan tanim karisimda bilinmez.
-- Her eski materyal icin tank materyaline bir TRANSFER_IN yazilir (source_material_id = eski, soy grafi bu bagdan
-- yurur), eskiler kapanir. Etkilenen tanklarin bakiyeleri yeniden hesaplanir; planlanmis ve devam eden dolumlarin
-- eski materyallere ayrilmis miktarlari tank materyaline (cevrilerek, kosu basina toplanip) tasinir.
-- Veri tahmin edilmez: cevrim icin yogunluk yoksa ya da onay bekleyen bir uretim cikisi/transferi birlesen bir
-- materyali gosteriyorsa migration durur (bkz. docs/GO_LIVE.md 2. adim).

create temporary table merge_source on commit drop as
select m.id, m.current_location_id as storage_unit_id, m.production_product_type_id as type_id,
    m.production_batch_id as batch_id, m.quantity, m.unit
from materials m
join storage_units u on u.id = m.current_location_id and u.kind = 'final'
where m.current_location_type = 'storage_unit' and m.status = 'active'
    and exists (select 1 from materials o
        where o.current_location_type = 'storage_unit' and o.current_location_id = m.current_location_id
            and o.status = 'active' and o.production_product_type_id = m.production_product_type_id
            and o.origin_type <> 'TANK');

-- Grup: tank + tip. Birim tipin birimi, tanimsizsa gruptaki ilk materyalinki; yogunluk tipin, yoksa urun varsayilani.
create temporary table merge_group on commit drop as
select s.storage_unit_id, s.type_id, t.production_product_id as product_id,
    coalesce(case when t.unit in ('KG', 'LITRE') then t.unit end, (array_agg(s.unit order by s.id))[1]) as unit,
    coalesce(case when t.density_kg_per_litre > 0 then t.density_kg_per_litre end,
        case when p.default_density_kg_per_litre > 0 then p.default_density_kg_per_litre end) as density,
    count(*) as size, null::bigint as material_id
from merge_source s
join production_product_types t on t.id = s.type_id
left join production_products p on p.id = t.production_product_id
group by s.storage_unit_id, s.type_id, t.production_product_id, t.unit, t.density_kg_per_litre,
    p.default_density_kg_per_litre;

do $$
declare
    invalid bigint;
begin
    select count(*) into invalid
    from merge_source s
    join merge_group g on g.storage_unit_id = s.storage_unit_id and g.type_id = s.type_id
    where s.unit <> g.unit and g.density is null;
    if invalid > 0 then
        raise exception 'son tanklarda yogunlugu tanimsiz oldugu icin birimi cevrilemeyen % materyal var', invalid;
    end if;

    select count(*) into invalid
    from batch_processes bp
    join production_stage_extensions e on e.production_stage_id = bp.production_stage_id and e.is_enabled
    join stage_extension_types t on t.id = e.stage_extension_type_id and t.key = 'storage_movement'
    left join lateral (
        select nullif(trim(v.value_text), '') as action
        from production_stage_extension_values v
        join stage_extension_type_fields f on f.id = v.stage_extension_type_field_id and f.field_key = 'action'
        where v.production_stage_extension_id = e.id
        order by v.id
        limit 1
    ) pinned on true
    where bp.status = 'awaiting_approval'
        and coalesce(pinned.action, bp.operation_data ->> 'action', 'inbound') in ('outbound', 'transfer')
        and (bp.operation_data ->> 'material_id' in (select id::text from merge_source)
            or (bp.operation_data ->> 'material_id' is null
                and exists (select 1 from merge_source s where s.batch_id = bp.production_batch_id)));
    if invalid > 0 then
        raise exception 'birlesecek bir son tank materyalini gosteren onay bekleyen % depo hareketi var', invalid;
    end if;
end
$$;

alter table merge_source add column converted numeric;

update merge_source s
set converted = round(case
        when s.unit = g.unit then s.quantity
        when s.unit = 'LITRE' then s.quantity * g.density
        else s.quantity / g.density
    end, 4)
from merge_group g
where g.storage_unit_id = s.storage_unit_id and g.type_id = s.type_id;

with created as (
    insert into materials (production_product_type_id, origin_type, quantity, unit, status, current_location_type,
        current_location_id, created_at, updated_at)
    select g.type_id, 'TANK', (select sum(s.converted) from merge_source s
            where s.storage_unit_id = g.storage_unit_id and s.type_id = g.type_id),
        g.unit, 'active', 'storage_unit', g.storage_unit_id, now(), now()
    from merge_group g
    order by g.storage_unit_id, g.type_id
    returning id, current_location_id, production_product_type_id
)
update merge_group g
set material_id = c.id
from created c
where c.current_location_id = g.storage_unit_id and c.production_product_type_id = g.type_id;

insert into storage_movements (storage_unit_id, material_id, source_material_id, source_type, movement_type,
    transfer_reference, quantity, unit, note, moved_at, created_at, updated_at)
select g.storage_unit_id, g.material_id, s.id, 'INTERNAL_TRANSFER', 'TRANSFER_IN', 'BIRLESTIRME',
    round(s.converted, 2), g.unit, 'Son tank materyalleri birlestirildi', now(), now(), now()
from merge_source s
join merge_group g on g.storage_unit_id = s.storage_unit_id and g.type_id = s.type_id
order by s.id;

-- Attribute degerleri: materyal ve tanim basina ilk satir (calisma anindaki gibi).
create temporary table merge_value on commit drop as
select distinct on (v.material_id, v.production_product_attribute_definition_id)
    v.id, v.material_id, v.production_product_attribute_definition_id as definition_id,
    coalesce(v.value_decimal, v.value_integer) as number,
    jsonb_build_array(v.value_boolean, v.value_integer, v.value_decimal, v.value_text, v.value_json) as signature,
    a.data_type
from material_attribute_values v
left join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
left join attribute_definitions a on a.id = d.attribute_definition_id
where v.material_id in (select id from merge_source)
order by v.material_id, v.production_product_attribute_definition_id, v.id;

create temporary table merge_attribute on commit drop as
select g.material_id, v.definition_id, g.size, count(*) as present, min(v.data_type) as data_type,
    bool_and(v.number is not null) as numbers, count(distinct v.signature) as variants,
    sum(v.number * s.converted) / sum(s.converted) as mean,
    (array_agg(v.id order by s.id))[1] as first_value_id
from merge_value v
join merge_source s on s.id = v.material_id
join merge_group g on g.storage_unit_id = s.storage_unit_id and g.type_id = s.type_id
group by g.material_id, v.definition_id, g.size;

-- Tek materyalli grup ya da hepsinde ayni deger: ilk satir aynen kopyalanir.
insert into material_attribute_values (material_id, production_product_attribute_definition_id, value_boolean,
    value_integer, value_decimal, value_text, value_json, created_at, updated_at)
select a.material_id, a.definition_id, v.value_boolean, v.value_integer, v.value_decimal, v.value_text, v.value_json,
    now(), now()
from merge_attribute a
join material_attribute_values v on v.id = a.first_value_id
where a.present = a.size
    and (a.size = 1 or (coalesce(a.data_type, '') not in ('decimal', 'integer') and a.variants = 1))
order by a.material_id, a.definition_id;

-- Sayisal: agirlikli ortalama.
insert into material_attribute_values (material_id, production_product_attribute_definition_id, value_integer,
    value_decimal, created_at, updated_at)
select a.material_id, a.definition_id,
    case when a.data_type = 'integer' then round(a.mean) end,
    case when a.data_type = 'decimal' then round(a.mean, 4) end,
    now(), now()
from merge_attribute a
where a.present = a.size and a.size > 1 and a.data_type in ('decimal', 'integer') and a.numbers
order by a.material_id, a.definition_id;

-- Planlanmis ve devam eden dolumlar: eski materyale ayrilan miktar tank materyalinin birimine cevrilip kosu basina
-- toplanir.
create temporary table merge_run on commit drop as
select rm.finished_product_run_id as run_id, g.material_id,
    sum(round(case
        when s.unit = g.unit then rm.quantity_used
        when s.unit = 'LITRE' then rm.quantity_used * g.density
        else rm.quantity_used / g.density
    end, 4)) as quantity,
    min(rm.created_at) as created_at
from finished_product_run_materials rm
join finished_product_runs r on r.id = rm.finished_product_run_id and r.status in ('planned', 'in_progress')
join merge_source s on s.id = rm.source_material_id
join merge_group g on g.storage_unit_id = s.storage_unit_id and g.type_id = s.type_id
group by rm.finished_product_run_id, g.material_id;

delete from finished_product_run_materials rm
using finished_product_runs r
where r.id = rm.finished_product_run_id and r.status in ('planned', 'in_progress')
    and rm.source_material_id in (select id from merge_source);

insert into finished_product_run_materials (finished_product_run_id, source_material_id, quantity_used, created_at,
    updated_at)
select run_id, material_id, quantity, created_at, now()
from merge_run
order by run_id, material_id;

update materials
set status = 'consumed', current_location_type = null, current_location_id = null, updated_at = now()
where id in (select id from merge_source);

delete from storage_unit_balances
where storage_unit_id in (select storage_unit_id from merge_group);

insert into storage_unit_balances (storage_unit_id, production_product_id, current_quantity, unit, created_at,
    updated_at)
select m.current_location_id, t.production_product_id, round(sum(m.quantity), 2), m.unit, now(), now()
from materials m
join production_product_types t on t.id = m.production_product_type_id
where m.current_location_type = 'storage_unit' and m.status = 'active' and t.production_product_id is not null
    and m.current_location_id in (select storage_unit_id from merge_group)
group by m.current_location_id, t.production_product_id, m.unit
having sum(m.quantity) > 0
order by m.current_location_id, t.production_product_id, m.unit;
