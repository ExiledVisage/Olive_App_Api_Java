# Makineler: makine kaydi, sikimda makine adimlari, makine takibi

Durum (2026-11-24): API 1. adim (7e9ac74), 2. adim (e1792fe), 3. adim (97d887c); Flutter 4. adim (2079f6d), 5. adim
(6a20dee, feat/java-api). 6. adim: sunucuya kuruldu (2026-11-24; DB sifirlandi, APK 1.0.36).

## Amac

- Makine kendi basina bir varliktir (ayri sinif, ayri sekme). Asamaya ya da `process_type_fields`'a bagli degildir;
  asama makineyi yalnizca kullanir. Asama silinse de makine ve gecmis kayitlari kalir.
- Sikim, makinelerin toplamidir: parti sirayla Kirici -> Malaksor -> Dekantor -> Separator'dan gecer. Sikim
  ekraninda "2/4" ilerleme; gecilen makineye dokununca sure ve makineye ozel degerler.
- Makine sekmesi: son calisma, su an hangi partide, hangi partilerle calisti, son 2-3 haftada sicaklik/devir gibi
  degerlerin seyri, toplam calisma suresi.
- Esnek: her tipten birden fazla makine, her makinede bir ya da daha cok parca (ornek: malaksorun 3 haznesi). Tek
  secenek varsa operator secim yapmaz.

## Kavramlar

| Kavram | Ornek | Ne tutar |
|---|---|---|
| Makine tipi | Malaksor | Islev aciklamasi, tipe ozel olcum alanlari (parent) |
| Makine tipi alani | Malaksor: sicaklik (°C) | Ad, anahtar, veri tipi, kapsam, birim, zorunlu mu, sira, min/max |
| Makine | Malaksor 1 | Fabrikadaki cihaz: kod, marka/model, kapasite, aktif mi, bilgi alanlari (faz sayisi...) |
| Makine parcasi | Hazne 1, Hazne 2, Hazne 3 | Makinenin bagimsiz calisan birimi; her makinede en az bir tane |
| Makine adimi (calisma) | P-261104-01-1 partisi Hazne 2'de 09:10-09:52 | Parti asamasi, sira, parca, baslangic/bitis |
| Adim degeri | Hazne 2, sicaklik 27.5 | Calismada girilen olcumler |

Neden makine basina ayri tablo degil: yeni makine turu ya da yeni olcum ekranlardan eklenir, migration ve kod
gerekmez. Alanlar `process_type_fields` gibi tanimdir ama makine tipine aittir, asamalarla bagi yoktur.

## Veritabani (yeni modul `machine`, migration klasoru `machine/`, V14_xx)

- `machine_types`: id, key (unique), name, description, sort_order, is_active.
- `machine_type_fields`: id, machine_type_id (RESTRICT), field_key, label, data_type (decimal/integer/text/boolean),
  scope, unit, is_required, sort_order, min_value, max_value, is_active. Unique (machine_type_id, field_key).
  - scope `run`: her calismada girilen olcum; `machine`: makine kaydinda duran bilgi (kirici tipi, faz sayisi).
    Ikisi de ekrandan eklenir, pasife alinir ya da (kullanilmamissa) silinir.
- `machines`: id, machine_type_id (RESTRICT), code (unique), name, brand, model, capacity_kg_per_hour, note,
  attributes (jsonb, field_key -> deger; tipin `machine` alanlariyla denetlenir), is_active.
- `machine_parts`: id, machine_id (RESTRICT), code, name, sort_order, is_active. Unique (machine_id, code).
- Asamanin makine sirasi ayri tablo degil, `machine_route` uzantisinin `machine_types` ayaridir (value_json, tip
  anahtarlari sirayla). Asama yonetimindeki uzanti ayar ekrani kullanilir; tanimsiz ya da pasif anahtarin adimi
  baslatilamaz.
- `machine_runs`: id, batch_process_id (CASCADE), step_order, machine_type_id (RESTRICT), machine_part_id
  (RESTRICT), started_at, finished_at, note. Unique (batch_process_id, step_order).
- `machine_run_values`: id, machine_run_id (CASCADE), machine_type_field_id (RESTRICT), value_decimal, value_integer,
  value_text, value_boolean. Unique (machine_run_id, machine_type_field_id).

Silme: kullanilmis makine, parca, tip ve tip alani silinmez (RESTRICT), yalnizca pasife alinir; pasif olan yeni
calismada secilemez ama gecmiste gorunur.

## Asama eklentisi: `machine_route` (Makine hatti)

- `stage_extension_types` yeni tip (8, `machine_route`). Ayari: `machine_types` = ["kirici", "malaksor", ...].
- Eklentisi olan asamada partinin her makine tipi icin bir adim acilir; ekran "tamamlanan / toplam" gosterir.
- Kronometre ile iliski (kullanici karari 2026-11-23): Sikim'in kendi kronometresi (`timer`) kalir, makine
  adimlari onun icinde ayri kronometrelerdir. Seed'de Sikim'de `timer` + `machine_route` birlikte.
  - Makine adimi, asama kronometresi baslamadan baslamaz.
  - Asama kronometresi, acik makine adimi varken bitirilemez.
  - Asama verisi (sondaki olcumler, yag elle, pirina kalan cikti) asama kronometresi bitince acilir (bugunku kural).
- Duzeyler: makinenin sabit bilgileri; asamaya ozel degerler (Sikim alanlari, yag/pirina); asama kronometresi;
  alt adim kronometresi ve makineye ozel degerler; makine istatistikleri.

## Kurallar

1. Adim sirasi: adim N, adim N-1 baslamadan baslamaz. Surekli akis oldugu icin saatler ortusebilir (N-1 bitmeden
   N baslayabilir). Adim yalnizca asama kronometresi calisirken baslar. Baslatilmamis adim asamanin tamamlanmasini
   engellemez ("2/4" gorunur).
2. Bir parca ayni anda tek partide calisir: acik calismasi olan parca baska partide secilemez ("Hazne 2 su an
   P-... partisinde"). Farkli parcalar ayni anda calisabilir.
3. Parca secimi: tipte tek aktif makine ve onda tek aktif parca varsa kendiliginden atanir; yoksa zorunlu secim.
4. Bitirirken tipin zorunlu alanlari dolu olmali. Min/max disi olcum kaydedilir, `out_of_range` ile isaretlenir.
   Makine bilgisinde (scope machine) min/max disi hatadir.
5. Saatler, parca, not ve olcumler sonradan duzeltilebilir (asama tamamlanmis olsa da); bitis baslangictan once
   olamaz.
6. Asama geri alinirsa (waiting) adimlar ve degerleri silinir (kronometre saatleri gibi).
7. Yetkiler: `machine.view` (sekme), `machine.manage` (tip, alan, makine, parca); adimlar `batch_process.fill`.

## API

- Yonetim: `/api/machine-types` (+ `/fields`), `/api/machines` (+ `/parts`); liste, detay, ekle, guncelle, pasife al.
- Parti tarafi:
  - `GET /api/batch-processes/{id}/machine-runs`: adimlar, secilebilir parcalar, durum.
  - `POST /api/batch-processes/{id}/machine-runs/{step}/start` (`machine_part_id` gerekirse).
  - `POST .../{step}/finish` (`values`: field_key -> deger, `note`), `PATCH .../{step}` (machine_part_id,
    started_at, finished_at, note, values).
  - Cevap: `completed`, `total`, `timer_started`, `timer_finished`, `steps[]` (step_order, machine_type_key,
    machine_type, status waiting/running/done, fields, parts (+ busy_batch_code), run).
- Makine sekmesi (`machine.view`):
  - `GET /api/machines` ve `/{id}`: `activity` = status (running/idle), running[] (parca, parti kodu, baslangic),
    last_run_at.
  - `GET /api/machines/{id}/runs?date_from&date_to` (`page`/`size` ile sayfali, en yeni once): parti, parca, sure,
    degerler.
  - `GET /api/machines/{id}/series?field=sicaklik&period` (ya da date_from/date_to): sayisal olcum alaninin zaman
    serisi, parca bazinda; noktada parti kodu ve aralik disi isareti.
  - `GET /api/machines/{id}/summary?period` (ya da date_from/date_to): donem `period` = 2d, 1w (varsayilan), 2w, 1m
    simdiye kadar; tarih araligi gunler dahil.
    - son calisma, su anki parti;
    - toplam calisma suresi, kullanim orani (calisma / donem suresi);
    - islenen parti sayisi ve kg, saatte islenen kg;
    - makineden gecen partilerin ezilen zeytini (Sikim girdisi), suzulen yagi (on dinlendirmeden cikan, kg) ve
      ortalama randimani (randiman makineye ayrilamaz: parti butun makinelerden gecer, yag sonda olculur). Parti
      bilgisini uretim modulu `MachineBatches` arayuzuyle verir (makine modulu uretime bagli degil);
    - alan basina ortalama / en dusuk / en yuksek, parca bazinda.

## Seed (baslangic tanimlari)

| Tip | Islev | Alanlar |
|---|---|---|
| Kirici | Zeytini cekirdegiyle ezip hamur yapar | elek capi (mm), devir (rpm), hamur sicakligi (°C), besleme debisi (kg/saat); bilgi: kirici tipi (cekicli/diskli) |
| Malaksor | Hamuru ilik ortamda karistirir, yag damlalarini birlestirir | sicaklik (°C), devir (rpm), inert gaz (evet/hayir), ceket suyu sicakligi (°C), hazne dolulugu (kg), mikro talk (%) |
| Dekantor | Yatay santrifuj: yagi pirina ve karasudan ayirir | devir (rpm), debi (kg/saat), su ilavesi (%, 3 fazlida), diferansiyel devir (rpm), su sicakligi (°C), yag cikis sicakligi (°C); bilgi: faz sayisi (2/3) |
| Separator | Dikey santrifuj: yagdaki suyu ve ince tortuyu alir | devir (rpm), yikama suyu sicakligi (°C), su ilavesi (l/saat), yag cikis sicakligi (°C), bosaltma araligi (dk) |

Seed'deki alanlar oneridir ve hepsi istege bagli (malaksor sicakligi haric); fabrika kullanmadiklarini pasife alir.

- Malaksor suresi alan degil, adimin baslangic/bitisinden hesaplanir. Sikim alanlari "Malakasyon Suresi /
  Sicakligi" kalkti (malaksor adimina tasindi).
- Ortam sicakligi ve zeytin giris sicakligi makineye degil sikima aittir: Sikim asama alani oldu.
- Her tipten bir makine, her makinede bir parca (Kirici 1, Malaksor 1 / Hazne 1, ...). Hazne sayisi bilinince
  ekrandan eklenir.
- Demo: her sikimda dort adim, makul degerler (malaksor 25-28 °C, 30-45 dk; dekantor 3000-3400 rpm ...).

## Flutter

- Yeni "Makineler" sekmesi:
  - Liste: makine, tip, durum (calisiyor: hangi parti / bos), son calisma.
  - Detay: ozet kartlari, son calismalar listesi (partiye dokununca parti asamasi), alan secmeli grafik (`fl_chart`,
    son 2-3 hafta, parca bazinda cizgi), parcalar.
  - Yonetim (`machine.manage`): makine tipleri ve alanlari, makineler ve parcalari.
- Sikim asamasi: kronometre karti kalir; altinda makine adimlari karti: dikey adimlar, her adimda durum, Baslat /
  Bitir, parca secimi, alan formu; bitenlere dokununca sure ve degerler; ust satirda "2/4".
- Makine detayinda donem secici (2 gun, 1 hafta, 1 ay, tarih araligi) ozet ve grafige uygulanir.
- Asama yonetimi: `machine_route` eklentisinde makine tiplerini sec ve sirala.

## Adimlar

1. API: tablolar, makine yonetimi uclari, testler.
2. API: `machine_route` eklentisi, adim uclari ve kurallar, kronometre baglantisi, demo, testler, senaryolar.
3. API: makine sekmesi uclari (calismalar, seri, ozet).
4. Flutter: Sikim'de makine adimlari karti.
5. Flutter: Makineler sekmesi ve yonetim ekranlari.
6. DB sifirla, deploy, APK (MACHINE_STAGES 6. adimla birlikte).

## Acik konular

- Dekantor 2 fazli mi 3 fazli mi (su ilavesi zorunlu mu)? Varsayilan: istege bagli.
- Operator her adimda Baslat/Bitir'e basabilir mi? Varsayilan: evet; basmazsa saatler sonradan duzeltilir.
- Bakim kaydi (ariza, temizlik) simdi mi sonra mi? Varsayilan: sonra.
