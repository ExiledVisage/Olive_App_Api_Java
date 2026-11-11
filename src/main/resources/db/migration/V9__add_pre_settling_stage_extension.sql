-- On dinlendirme asamasi (C3-C5): urun on dinlendirme tankina girer, asama tank bosaltilana kadar dinlenmede
-- (batch_processes.status = 'resting') bekler; bosaltmada tortunun hangi urun olacagi uzantinin cikti urunundan.
-- Durum serbest metin oldugu icin sema degismez. Uzanti tipleri veridir: bos veritabaninda (kurulum, testler) seed
-- getirir, dolu veritabaninda eklenir.

insert into stage_extension_types (key, name, description, created_at, updated_at)
select 'pre_settling', 'Pre-settling',
    'Bu asamada urun on dinlendirme tankina alinir; asama tank bosaltilinca tamamlanir, fark tortu olur.', now(), now()
where exists (select 1 from stage_extension_types)
    and not exists (select 1 from stage_extension_types where key = 'pre_settling');
