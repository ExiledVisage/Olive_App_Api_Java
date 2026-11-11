-- Tank turu: son tank (final) ya da on dinlendirme tanki (pre_settling). Var olan tanklar son tank olur;
-- kodunda, adinda ya da aciklamasinda "dinlen" gecen tanklar (canlida dinlenme tanklari yalnizca metinle
-- belirtiliyor) on dinlendirme tanki olur. Liste gecisten once GO_LIVE 2. adimda kontrol edilir.
-- On dinlendirme tanki tek urun tipiyle calisir: blend ve coklu urun/tip ayarlari kapatilir.

alter table storage_units add column kind varchar(255) not null default 'final';
alter table storage_units add constraint storage_units_kind_check check (kind in ('final', 'pre_settling'));

update storage_units set kind = 'pre_settling'
where concat_ws(' ', code, name, description) ~* 'd[iıİI]nlen';

update storage_units
set is_blend_tank = false, allow_multiple_production_product_types = false, allow_multiple_production_products = false
where kind = 'pre_settling';
