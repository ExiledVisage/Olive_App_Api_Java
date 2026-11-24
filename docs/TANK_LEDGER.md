# Tank defteri: islem gecmisini deftere donusturmek

Durum (2026-11-24): API 1. adim (TankLedger) tamam; 2-5 bekliyor. API ve Flutter birlikte.
Ilk plan (2026-11-20) ayri bir defter ucu ve sayfasi oneriyordu; kullanici karari ile yerine **var olan tank islem
gecmisi gelistirilir**.

## Sorun

Son tank materyali tank bosalana kadar acik kalir; o sure boyunca onlarca parti girer, onlarca dolum cikar.
Soy agaci bunu graf olarak cizince hem okunmaz hem telefonu kasar.

Ornek (dev sunucu, 2026-11-20): materyal 1138, TOPLAMA-GEM-01 tankindaki 3.052 L Gemlik Zeytinyagi: 159 dugum,
20 parti girisi (her biri 4 adimlik zincir), 24 dolum kosusu, 32 rafa koyma.

## Bugun ne var

- `GET /api/storage-units/{id}/movement-history?date_from&date_to&page&size` (`StorageMovementListing.history`):
  tankin butun hareketleri, en yeni once; `page`/`size` verilirse sayfali.
- Flutter tank sayfasi (`storage_unit_detail_page.dart`, `_HistorySection`): tarih araligi secilir, aralik **tek
  seferde** cekilir. Satir: hareket turu, urun, miktar, tarih, karsi depo, asitlik, not. Yalnizca dolum satiri
  tiklanir (kosu detayi).
- `GET /api/materials/{id}`: son 20 hareket. `GET /api/materials/{id}/lineage`: grafin tamami (siniri 500);
  tank materyalinde graf butun girdi ve ciktilarla cizilir.

Eksikler: bakiye yok; Flutter sayfalamiyor; giris satiri partiye/soy agacina gitmiyor; liste tank bazli (sezon
boyunca tankta birden fazla materyal olabilir), materyale suzulemiyor; tank materyalinin soy agaci sisiyor.

## Kararlar (kullanici)

1. Ayri defter sayfasi yok: **tankin islem gecmisi defter olur** (kredi karti ekstresi gibi: ne girdi, ne cikti,
   tarih sirasiyla, kalan ile).
2. Tank materyalinde soy agaci cizilmez; yerine o tankin bu materyale suzulmus gecmisi (defter) acilir.
3. Defterdeki parti girisi tiklanir: kaynak materyalin soy agacina gider (o zincir tankta biter).
4. Sayfali, sonsuz kaydirma.

## API

### 1. Islem gecmisine eklenenler (ayni uc)

`GET /api/storage-units/{id}/movement-history`:

- Yeni suzgec `material_id`: yalnizca o materyalin hareketleri (tank materyali defteri).
- Satira yeni alanlar (eski alanlar aynen kalir, Flutter kirilmaz):

| Alan | Aciklama |
|---|---|
| `direction` | `in` / `out` (IN, TRANSFER_IN, OPENING, ADJUSTMENT_IN girer; digerleri cikar) |
| `balance_after` | satirdan sonra **bu materyalin bu tanktaki** miktari (materyalin biriminde) |
| `source_batch` | giriste: kaynak materyal id ve kodu, parti/grup kodu, teslimat kodu, asama adi |
| `finished_product_run` | dolumda: kosu id, urun, sise adedi (bugun yalnizca id var) |
| `counterparty_material_id` | transferde: karsi tanktaki materyal (karsi defter icin) |

- Bakiye pencere fonksiyonu ile hesaplanir (`sum(+/-quantity) over (partition by storage_unit_id, material_id
  order by moved_at, id)`), tarih suzgeci ve sayfadan once; sayfa ya da aralik sinirinda kopmaz.
- Bakiye materyal basina: bir tankta farkli birimde (KG / LITRE) materyal olabildigi icin tank toplami verilmez.

### 2. Defter ozeti

`GET /api/storage-units/{id}/movement-history/summary?material_id` (ya da ilk sayfada `summary`): materyal,
urun tipi, birim, toplam giren, toplam cikan, kalan, giris/cikis sayisi, ilk ve son hareket zamani. `material_id`
yoksa tankin su anki materyalleri icin birer ozet. Ozette tank materyalinin attribute'lari (asitlik vb.):
bunlar zaten her giriste `TankMixAttributes.merge` ile kutleye gore agirlikli ortalama olarak guncelleniyor ve
tank sayfasinda gorunuyor; yeni hesap yok, ayni deger ozete konur.

### 2b. Giristen sonraki karisim degeri (yeni)

Bugun yalnizca **guncel** ortalama saklaniyor; gecmiste "su giristen sonra tank asitligi neydi" bilinmiyor.
Giris satirina `mix_after` eklenir: o giristen sonra tank materyalinin sayisal attribute degerleri. Saklanmaz,
hesaplanir (`TankLedger`): materyalin bu tanktaki hareketleri ve tank analizleri zaman sirasiyla yeniden oynatilir,
her giriste `merge` ile ayni kural uygulanir (tanktaki o anki miktar ile gelen miktara gore agirlikli ortalama;
cikis ortalamayi degistirmez ama sonraki girisin agirligini azaltir; girislerden birinde deger yoksa bos; analiz
olculen degeri yerine koyar, ayni saniyedeki harekette analiz sonra sayilir). Soy agacindaki tank uc dugumunun
snapshot'inda da gorunur.

### 3. Soy agaci tankta durur

- Odak tank materyaliyse cevap graf yerine buna isaret eder (`tank_ledger: {storage_unit_id, material_id}`);
  Flutter defteri acar.
- Odak siradan materyalse ve yol bir tank materyaline variyorsa tank **uc dugum** olur: "bu tanka girdi".
  Tankin diger girdileri ve ciktilari genisletilmez; tahmini pay (kutle orani) gosterilmez (karar 2026-11-24).
- Uc dugumde giris anindaki **snapshot**: tank adi, giris zamani, giren miktar, tankta giristen once ve sonra
  bulunan miktar (`balance_after` ile ayni hesap). Dokununca defter, o giris satirinin oldugu yerde acilir.

## Flutter

1. **Tank sayfasi islem gecmisi:** sonsuz kaydirma (sayfa 20); gun gun gruplu; giris yesil +, cikis kirmizi -,
   sagda kalan; tarih araligi suzgeci kalir; tankta birden fazla materyal varsa materyal secici.
2. **Satira dokunma:** parti girisi -> kaynak materyalin soy agaci; dolum -> kosu detayi (bugunku gibi);
   transfer -> karsi tankin gecmisi, o materyale suzulu.
3. **Materyal detayi:** tank materyalinde AppBar'daki "Soy agaci" ikonu "Defter" olur ve tankin gecmisini bu
   materyale suzulu acar; son 20 hareket kalir, "Tumu" ayni yere gider.
4. **Soy agaci sayfasi:** tank uc dugumu ayri gorunur; dokununca defter acilir.

## Adimlar

1. API: gecmise `material_id`, `direction`, `balance_after`, `mix_after`, kaynak parti ve kosu bilgisi; ozet;
   testler.
2. API: soy agaci tankta durur; senaryo kayitlari (material, storage) yeniden kaydedilir.
3. Flutter: gecmis sayfali defter, satir gecisleri, materyal secici.
4. Flutter: materyal detayi ve soy agaci tank dugumu.
5. Deploy (kullanici istediginde).

## Test

- Gecmis: sayfalama, sira, sayfa ve tarih siniri disinda kalan hareketlere ragmen dogru `balance_after`,
  `material_id` suzgeci, giris/cikis toplamlari, `mix_after` (deger eksik giriste bos).
- Soy agaci: siradan materyalde tankta durma; tank materyali odakta graf yok.

