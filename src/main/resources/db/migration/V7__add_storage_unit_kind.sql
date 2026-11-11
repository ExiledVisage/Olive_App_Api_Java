-- Tank turu: son tank (final) ya da on dinlendirme tanki (pre_settling). Var olan tanklar son tank olur.

alter table storage_units add column kind varchar(255) not null default 'final';
alter table storage_units add constraint storage_units_kind_check check (kind in ('final', 'pre_settling'));
