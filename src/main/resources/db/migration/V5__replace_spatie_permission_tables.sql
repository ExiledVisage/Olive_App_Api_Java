-- Spatie'nin morph'lu pivot tablolari yerine FK'li ve benzersiz kisitli uc tablo. Satirlar aynen tasinir.
-- Tasinamayan bir satir varsa migration durur: veri tahmin edilmez, sessizce atilmaz (bkz. docs/GO_LIVE.md).

do $$
declare
    invalid bigint;
begin
    select count(*) into invalid
    from (
        select model_type from model_has_roles
        union all
        select model_type from model_has_permissions
    ) assignment
    where model_type <> 'App\Models\User';
    if invalid > 0 then
        raise exception 'model_type degeri kullanici olmayan % rol/izin atamasi var', invalid;
    end if;

    select count(*) into invalid
    from (
        select model_id from model_has_roles
        union all
        select model_id from model_has_permissions
    ) assignment
    where not exists (select 1 from users u where u.id = assignment.model_id);
    if invalid > 0 then
        raise exception 'var olmayan kullaniciya ait % rol/izin atamasi var', invalid;
    end if;

    select count(*) into invalid from (select name from roles group by name having count(*) > 1) duplicate;
    if invalid > 0 then
        raise exception 'farkli guard ile ayni adi tasiyan % rol var', invalid;
    end if;

    select count(*) into invalid from (select name from permissions group by name having count(*) > 1) duplicate;
    if invalid > 0 then
        raise exception 'farkli guard ile ayni adi tasiyan % izin var', invalid;
    end if;
end
$$;

create table user_roles (
    user_id bigint not null references users (id) on delete cascade,
    role_id bigint not null references roles (id) on delete cascade,
    primary key (user_id, role_id)
);

create index user_roles_role_id_index on user_roles (role_id);

create table user_permissions (
    user_id bigint not null references users (id) on delete cascade,
    permission_id bigint not null references permissions (id) on delete cascade,
    primary key (user_id, permission_id)
);

create index user_permissions_permission_id_index on user_permissions (permission_id);

create table role_permissions (
    role_id bigint not null references roles (id) on delete cascade,
    permission_id bigint not null references permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);

create index role_permissions_permission_id_index on role_permissions (permission_id);

insert into user_roles (user_id, role_id) select model_id, role_id from model_has_roles;
insert into user_permissions (user_id, permission_id) select model_id, permission_id from model_has_permissions;
insert into role_permissions (role_id, permission_id) select role_id, permission_id from role_has_permissions;

drop table model_has_roles;
drop table model_has_permissions;
drop table role_has_permissions;

alter table roles drop column guard_name;
alter table roles add constraint roles_name_unique unique (name);

alter table permissions drop column guard_name;
alter table permissions add constraint permissions_name_unique unique (name);

alter table users drop column remember_token;
