-- Son tank: tank ve urun tipi basina tek tank materyali. Tanka giris hareketi, karistigi tank materyalini
-- (material_id) ve gelen materyali (source_material_id) gosterir; soy grafi bu bagdan yurur.

alter table storage_movements add column source_material_id bigint null;
alter table storage_movements
    add constraint storage_movements_source_material_id_foreign
        foreign key (source_material_id)
        references materials (id)
        on delete restrict;
create index storage_movements_source_material_id_index on storage_movements (source_material_id);

-- Son tank analizi: olculen deger hesaplananin yerine gecer; her olcum hesaplanan, olculen ve farkla saklanir.

create table tank_measurements (
    id bigserial not null primary key,
    storage_unit_id bigint not null,
    material_id bigint not null,
    production_product_attribute_definition_id bigint not null,
    calculated_value decimal(14, 4) null,
    measured_value decimal(14, 4) not null,
    difference decimal(14, 4) null,
    measured_by_user_id bigint null,
    note text null,
    measured_at timestamp(0) without time zone not null,
    created_at timestamp(0) without time zone null,
    updated_at timestamp(0) without time zone null
);

alter table tank_measurements
    add constraint tank_measurements_storage_unit_id_foreign
        foreign key (storage_unit_id)
        references storage_units (id)
        on delete restrict;

alter table tank_measurements
    add constraint tank_measurements_material_id_foreign
        foreign key (material_id)
        references materials (id)
        on delete restrict;

alter table tank_measurements
    add constraint tank_measurements_definition_id_foreign
        foreign key (production_product_attribute_definition_id)
        references production_product_attribute_definitions (id)
        on delete restrict;

alter table tank_measurements
    add constraint tank_measurements_measured_by_user_id_foreign
        foreign key (measured_by_user_id)
        references users (id)
        on delete set null;

create index tank_measurements_storage_unit_id_index on tank_measurements (storage_unit_id);
create index tank_measurements_material_id_index on tank_measurements (material_id);

-- Tank olcumu icin ayri izin. Bos veritabaninda (kurulum, testler) roller yoktur, izinleri seed getirir; dolu
-- veritabaninda izin eklenir ve admin rolune, stok duzeltme izni olan rollere ve kullanicilara verilir.

insert into permissions (name, created_at, updated_at)
select 'storage_unit.measure', now(), now()
where exists (select 1 from roles) and not exists (select 1 from permissions where name = 'storage_unit.measure');

insert into role_permissions (role_id, permission_id)
select r.id, m.id
from roles r
join permissions m on m.name = 'storage_unit.measure'
where r.name = 'admin'
    or exists (select 1 from role_permissions rp join permissions a on a.id = rp.permission_id
        where rp.role_id = r.id and a.name = 'storage_movement.adjustment')
on conflict do nothing;

insert into user_permissions (user_id, permission_id)
select up.user_id, m.id
from user_permissions up
join permissions a on a.id = up.permission_id and a.name = 'storage_movement.adjustment'
join permissions m on m.name = 'storage_unit.measure'
on conflict do nothing;
