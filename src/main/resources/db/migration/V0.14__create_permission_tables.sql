-- Laravel: 2026_03_12_123915_create_permission_tables.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

create table "permissions" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "guard_name" varchar(255) not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "permissions"
    add constraint "permissions_name_guard_name_unique" unique ("name", "guard_name");

create table "roles" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "guard_name" varchar(255) not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "roles" add constraint "roles_name_guard_name_unique" unique ("name", "guard_name");

create table "model_has_permissions" (
    "permission_id" bigint not null,
    "model_type" varchar(255) not null,
    "model_id" bigint not null
);

create index "model_has_permissions_model_id_model_type_index"
    on "model_has_permissions" ("model_id", "model_type");

alter table "model_has_permissions"
    add constraint "model_has_permissions_permission_id_foreign"
        foreign key ("permission_id")
        references "permissions" ("id")
        on delete cascade;

alter table "model_has_permissions" add primary key ("permission_id", "model_id", "model_type");

create table "model_has_roles" (
    "role_id" bigint not null,
    "model_type" varchar(255) not null,
    "model_id" bigint not null
);

create index "model_has_roles_model_id_model_type_index"
    on "model_has_roles" ("model_id", "model_type");

alter table "model_has_roles"
    add constraint "model_has_roles_role_id_foreign"
        foreign key ("role_id")
        references "roles" ("id")
        on delete cascade;

alter table "model_has_roles" add primary key ("role_id", "model_id", "model_type");

create table "role_has_permissions" (
    "permission_id" bigint not null,
    "role_id" bigint not null
);

alter table "role_has_permissions"
    add constraint "role_has_permissions_permission_id_foreign"
        foreign key ("permission_id")
        references "permissions" ("id")
        on delete cascade;

alter table "role_has_permissions"
    add constraint "role_has_permissions_role_id_foreign"
        foreign key ("role_id")
        references "roles" ("id")
        on delete cascade;

alter table "role_has_permissions" add primary key ("permission_id", "role_id");
