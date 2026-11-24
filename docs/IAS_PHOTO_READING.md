# IAS ekranini fotograftan okuma

Durum: ertelendi (2026-11-24, kullanici: "simdilik aklinda tut, bir kenara yaz"). Kod yazilmadi. IAS olcumunun kendisi
yapildi (`docs/GREENOPS_FEATURES.md` 3, asamadan bagimsiz olcum ucu); bu, ona eklenecek bir kolaylik.

## Ne yapar

Operator IAS cihazinin ekranini telefonla ceker, Claude ekrandaki degerleri okur, mevcut IAS olcum penceresi bu
degerlerle dolu acilir; operator kontrol edip kaydeder. Okunan degerler kendiliginden kaydedilmez. GreenOps'ta ayni
ozellik var (IAS ekrani -> Claude vision -> form).

## Akis

**Flutter**
1. IAS olcum penceresinde (zeytin / pirina) "Fotograftan oku" dugmesi.
2. Kamera (`image_picker`), fotograf telefonda kucultulur.
3. "Okunuyor..." beklemesi; donen degerler alanlara dolar, okunamayan alan bos ve "kontrol edin" isaretli.
4. Kaydet, var olan `POST /api/production-batches/{id}/ias-measurements` ucuyla.

**API**
- `POST /api/production-batches/{id}/ias-measurements/read`: fotograf ve tur (`olive`/`pomace`) alir, okunan
  degerleri doner, kaydetmez. Yetki `batch_process.fill`, kullanici basina dakikalik sinir.
- Claude cagrisi resmi Java SDK'si (`anthropic-java`) ile; model `claude-opus-5-5`, dusuk effort (basit okuma),
  gorsel girdi + yapilandirilmis cikti (sema: yag, nem, KPY, asitlik, endustriyel randiman, numune no, ekranda
  gorunen urun adi). Istemde ture gore kural (pirinada asitlik ve randiman yok).
- Sunucu dogrular: 0-100 araligi, pirinada asitlik yok, ekrandaki urun turu secilenle uyusmuyorsa uyari.
- `stop_reason: refusal` ve hata durumlari "okunamadi, elle girin" olarak doner.
- Anahtar yoksa uc "ozellik kapali" doner; Flutter dugmeyi gostermez.
- Testlerde gercek Claude cagrilmaz (sahte okuyucu).

## Gerekenler / acik kararlar

1. **Anthropic API anahtari** (kullanicinin hesabi, ucret oraya): sunucu `.env`'inde `ANTHROPIC_API_KEY`; kodda
   ve depoda durmaz. Anahtar konusmada paylasilmaz.
2. **Fotograf saklansin mi?** Oneri: saklanmasin, yalnizca okunan degerler.
3. **Gizlilik:** fotograf okunmak icin Anthropic'e gider (yalnizca cihaz ekrani, kisisel veri yok); kullanici
   onayi gerekli.
4. Yeni paketler: API'ye `anthropic-java`, Flutter'a `image_picker`.
5. Maliyet: okuma basina kabaca 1-2 kurus dolar (Opus 5.5, tek gorsel).

## Adimlar (basladiginda)

1. API: okuma ucu, Claude istemcisi (anahtar yoksa kapali), dogrulama, sahte okuyucuyla testler.
2. Flutter: "Fotograftan oku", kamera, doldurulmus pencere, test.
3. Sunucu: anahtarin `.env`'e eklenmesi, deploy, APK.
