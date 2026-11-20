# Tank materyali: soy agaci yerine defter

Durum: plan (2026-11-20), kod yazilmadi. API ve Flutter birlikte.

## Sorun

Son tank materyali tank bosalana kadar acik kalir; o sure boyunca onlarca parti girer, onlarca dolum cikar.
Soy agaci bunu graf olarak cizince hem okunmaz hem telefonu kasar.

Ornek (dev sunucu, 2026-11-20): materyal 1138, TOPLAMA-GEM-01 tankindaki 3.052 L Gemlik Zeytinyagi.

| Kalem | Sayi |
|---|---|
| Dugum / kenar | 159 / 158 |
| Tanka giren parti | 20 (her biri 4 adimlik zincir: Ön Dinlenme, Zeytinyagi Tartimi, Pirina Tartimi, Ikinci Tartim) |
| Zincir + zeytin dugumleri | 100 |
| Dolum kosusu / rafa koyma | 24 / 32 |

## Kararlar (kullanici)

1. **Tank materyalinde soy agaci yok.** Yerine kredi karti ekstresi gibi bir **defter**: tanka ne girdi, ne cikti,
   tarih sirasiyla, kalan bakiye ile.
2. **Materyale tiklayinca** (tank materyaliyse) defter acilir.
3. **Girdiler tiklanabilir:** defterdeki her parti/grup girisi, o grubun kendi soy agacina gider.
4. **Sayfalama:** defter sayfali gelir (sonsuz kaydirma).

## Bugun ne var

- `GET /api/materials/{id}` (`MaterialService.movements`): **son 20** hareket; materyalin kendi hareketleri ve
  `source_material_id` ile tanka girisleri (`tank_material_id` dolu). Sayfalama yok.
- `GET /api/storage-units/{id}/movement-history` ve `/movements`: tankin (birimin) sayfali gecmisi; materyal bazli degil.
- `GET /api/materials/{id}/lineage`: grafin tamami tek seferde (dugum siniri 500). Tank materyalinde dugumler
  `lineage_role = tank`.
- Flutter `material_detail_page.dart`: AppBar'da "Soy agaci" ikonu -> `MaterialLineagePage`; hareket listesi
  (son 20), `tank_material_id` satiri tank materyaline gider.

## API

### 1. Defter ucu

`GET /api/materials/{id}/ledger?page&size` (yetki: `material.view`), en yeni once.

Ozet (ilk sayfada ya da ayri alan): tank, urun tipi, birim, toplam giren, toplam cikan, kalan, giris/cikis
sayisi, acilis ve (varsa) kapanis zamani.

Satir:

| Alan | Aciklama |
|---|---|
| `moved_at`, `movement_type` | giris (TRANSFER_IN), dolum, cikis, transfer, sayim farki |
| `quantity` (isaretli), `unit` | + giris, - cikis; materyalin biriminde |
| `balance_after` | satirdan sonraki bakiye (ekstre gibi) |
| `source` | giriste: kaynak materyal id, parti/grup kodu, teslimat kodu, asama adi |
| `run` | dolumda: kosu id, urun, sise adedi |
| `counterparty` | transferde: karsi tank ve (varsa) karsi tank materyali id |

Bakiye: sayfalamadan bagimsiz olmali (pencere fonksiyonu ile kumulatif toplam), sayfa sinirinda kopmaz.

### 2. Soy agaci tankta durur

- Odak tank materyaliyse ucun cevabi buna isaret eder (ya da Flutter `lineage_role` ile karar verir); graf cizilmez.
- Odak siradan materyalse ve yol bir tank materyaline variyorsa: tank **uc dugum** olur (bu materyalin tanka
  giren miktari ile); tankin diger girdileri ve ciktilari genisletilmez.

### 3. Materyal detayi

Son 20 hareket kalir; tank materyalinde "Tumu" defter ucuna gider.

## Flutter

1. **Defter sayfasi** (yeni): ustte ozet karti; altta gun gun gruplanmis satirlar (giris yesil +, cikis kirmizi -,
   sagda bakiye); sonsuz kaydirma ile sayfalama.
2. **Satira dokunma:**
   - parti/grup girisi -> kaynak materyalin soy agaci (o grubun zinciri, tankta biter);
   - dolum -> kosu detayi;
   - transfer -> karsi tank materyalinin defteri.
3. **Materyal detayi:** tank materyalinde AppBar ikonu "Soy agaci" yerine "Defter".
4. **Soy agaci sayfasi:** tank uc dugumu ayri gorunur; dokununca defter acilir.

## Test

- Defter: sayfalama, sira, sayfa sinirinda dogru `balance_after`, giris/cikis toplamlari.
- Soy agaci: siradan materyalde tankta durma; tank materyali odakta graf yok.
- Senaryo kayitlari (material) yeniden kaydedilir.

## Acik sorular

- Siradan materyalin agacinda tankin otesi: yalnizca "bu tanka girdi" mi, yoksa giristen sonraki dolumlar
  **tahmini pay** (kutle orani) ile mi gosterilsin? Geri cagirma icin degerli ama kesin degil.
- Defter ozetinde lab ortalamalari (agirlikli ortalama attribute'lar) gosterilsin mi?
