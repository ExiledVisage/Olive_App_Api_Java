# GreenOps'tan alinan ozellikler

Durum: plan (2026-11-24), acik sorular cevaplandi, kod yazilmadi. API ve Flutter birlikte. Kaynak: greenops-suite
incelemesi (2026-11-23); GreenOps'tan kod alinmaz, ozellik bizim yapimiza (asama eklentileri, materyal modeli, sunucu
kurallari, testler) uyarlanir. Makine parametreleri ayrica yapildi (`docs/MACHINES.md`).

Kullanici secimi (2026-11-24): onerilen dort hizli kazanim ve QR.

| # | Ozellik | Kisaca |
|---|---|---|
| 1 | Zeytin alim fiyati ve tedarik kanali | Teslimatta kg fiyati, nakliye, kanal; tedarikci ve donem toplamlari |
| 2 | Dolumda kirik / hatali ambalaj | Dolum kosusuna fire kaydi; ambalaj lotundan duser |
| 3 | IAS olcumu ve KPY | Zeytin ve pirinada yag, nem, asitlik; kuru bazda yag (KPY) hesaplanir |
| 4 | Tank seviyesi mm'den | Tank geometrisi; cetvel mm -> litre ve kg; defter bakiyesiyle fark |
| 5 | QR etiket ve izlenebilirlik sayfasi | Parti/lot etiketi; uygulamada QR okutma; sise QR.indan acik sayfa |

GreenOps'taki ama bu planda olmayanlar: IAS ekranini fotograftan AI ile okuma, saatlik dolum gramaj kontrolu, maliyet
zinciri (cari, parti/mamul maliyeti), satis ve kalite modulleri. Sonra ayrica ele alinir.

## 1. Zeytin alim fiyati ve tedarik kanali

**Bugun:** `production_batches` teslimati tutar (kantar fisi, plaka, kg, tedarikci, tarla); fiyat ve kanal yok.

**Veri** (`production_batches`, yalnizca teslimat yani `parent_batch_id` bos kayit):

| Kolon | Aciklama |
|---|---|
| `supply_channel` | `farmer` (ciftci), `trader` (tuccar), `own_grove` (kendi bahcemiz); bos olabilir |
| `price_per_kg` | TL/kg, KDV haric, numeric(12,4); kendi bahcede bos |
| `vat_rate` | KDV orani %, numeric(5,2); fiyat girildiyse zorunlu (formda son kullanilan oran gelir) |
| `transport_cost` | nakliye, TL, toplam; bos olabilir |
| `price_note` | ornek: "pesin", "hasat sonu odeme" |

Hesaplanan (saklanmaz), hepsi KDV haric ve KDV dahil iki haliyle (kullanici karari): `price_per_kg`,
`purchase_total = quantity_kg x price_per_kg + transport_cost`, `cost_per_kg`. Para birimi TL.

**Kurallar:** fiyat >= 0; gruba bolunmus partide fiyat teslimattadir, gruplara kg payiyla yansir (yalnizca
gosterim). Tamamlanmis teslimatta fiyat sonradan duzeltilebilir (odeme sonradan netlesir).

**Yetki:** yeni `production_batch.price` (gor ve duzenle): admin, planner, food_engineer (muhendis); operator
rolleri (operator, production_operator, warehouse_operator) gormez. Yetkisi olmayana fiyat alanlari cevapta
gelmez.

**API:** teslimat olustur/duzenle istegine alanlar; `GET /api/suppliers/{id}/purchases?date_from&date_to`
(teslimatlar, kg, tutar, ortalama kg fiyati); analitik raporlara "alim tutari" ve "ortalama zeytin fiyati".

**Flutter:** teslimat formunda kanal ve fiyat (yetkiye gore), teslimat detayinda tutar; tedarikci sayfasinda alim
ozeti (donem secici).

## 2. Dolumda kirik / hatali ambalaj

**Bugun:** ambalaj lotunda elle `adjustment` (reason SCRAP) var, kosuya bagli degil; kosu tamamlaninca tahsis
edilen ambalaj tuketilir.

**Veri:** `packaging_lot_movements` zaten `finished_product_run_id` ve `SCRAP` tasiyor; yeni tablo yok. Kayit:
kosu, ambalaj kalemi, lot (verilmezse kosunun o kalem icin tahsis ettigi lot, FIFO), adet, sebep (`broken`,
`defective`, `other`), not.

**Kurallar:** kosu `in_progress` ya da `completed` iken; lotta yeterli adet olmali; fire kosunun uretim adedini
degistirmez. Geri alinabilir (ters hareket) ayni gun icinde ya da yetkiyle.

**API:** `POST /api/finished-product-runs/{id}/packaging-scrap`, `GET .../packaging-scrap`,
`DELETE .../packaging-scrap/{movementId}`; kosu cevabinda kalem basina fire toplami ve fire orani
(fire / (uretilen + fire)). Analitikte ambalaj fire orani.

**Flutter:** kosu detayinda "Kirik / hatali ambalaj" bolumu ve ekleme penceresi (kalem, adet, sebep, not).

## 3. IAS olcumu ve KPY

**Bugun:** `analysis` eklentisi materyale attribute degeri yazar. Zeytinin `moisture` ve `oil_ratio` attribute'lari
tanimli ama olcen asama yok; pirinada hic yok. Asitlik analizi yalnizca yagda.

**Kavramlar:**
- IAS (NIR) cihazi zeytin hamuru ya da pirinada yag %, nem %, (zeytinde) serbest asitlik olcer.
- **KPY** (kuru bazda yag) = yag % / (100 - nem %) x 100. Nemden bagimsiz karsilastirma saglar.
- Pirinada KPY, pirinada kalan yagi gosterir: dusukse ekstraksiyon iyi. Pirinada kalan yag kg =
  pirina kg x pirina yag % / 100.

**GreenOps'ta:** parti basina iki IAS olcumu: sikimdan once "IAS Oncesi" (tane zeytin: YPY yas baz yag %, nem %,
KPY %, asitlik %, endustriyel randiman %) ve sikim bittikten sonra "IAS Sonrasi" (poma/pirina: YPY, nem, KPY;
asitlik ve randiman yok). Pirina olcumu sikim bitmeden yapilamaz; v2'de dinlendirme tankina almadan once zorunlu,
v3'te atlanabilir. KPY cihazdan gelmezse YPY / (1 - nem/100) ile hesaplanir. Olcumde numune no ve operator tutulur.

**Fabrikalarda:** surekli hatta zeytin numunesi kirmadan once (kantar/besleme bunkeri), pirina numunesi
dekantorun pirina cikisindan sikim sirasinda ya da sonunda alinir; NIR cihazi (IAS gibi) dakikalar icinde yag ve
nem verir, numune laboratuvara da gonderilebilir. Iki fazli pirinada nem yuksek (yaklasik %55-70) oldugundan
karsilastirma KPY ile yapilir; pirina KPY'si dekantor verimini, zeytin endustriyel randimani beklenen randimani
gosterir. Her parti olculmeyebilir; olcum bu yuzden istege bagli olmali.

**Karar:** GreenOps'taki gibi iki ayri, istege bagli (`is_required = false`) asama: Sikim'dan once
"IAS Zeytin Olcumu" (analysis, zeytin) ve Sikim'dan hemen sonra "IAS Pirina Olcumu" (analysis, pirina; pirina
Sikim'daki donusumle olustugu icin sikim bitmeden yapilamaz, bu zaten asama sirasindan gelir).

**Seed / tanim:**
- Attribute: `kpy` (decimal, %, "Kuru Bazda Yag"), `industrial_yield` (decimal, %, "Endustriyel Randiman"),
  `sample_no` (string, "Numune No").
- Zeytin: `oil_ratio` (YPY), `moisture`, `kpy`, `acidity`, `industrial_yield`, `sample_no`.
- Pirina: `oil_ratio`, `moisture`, `kpy`, `sample_no`.

**Kod:** `kpy` hesaplanan attribute: ayni materyalde `oil_ratio` ve `moisture` yazildiginda `kpy` verilmediyse sunucu
hesaplayip yazar; cihaz KPY verdiyse o kaydedilir (GreenOps gibi). Randiman kartinda beklenen randiman (zeytin
`industrial_yield`) ile gercek randiman yan yana ve "pirinada kalan yag" (pirina kg x pirina yag %) bilgi olarak;
analitikte zeytin yag % ve pirina KPY ortalamalari (cesit ve donem basina).

**Flutter:** analiz formu zaten dinamik; `kpy` salt okunur gosterilir. Parti detayinda IAS ozeti.

## 4. Tank seviyesi mm'den

**Bugun:** tank analizi (asitlik olcumu) var; miktar olcumu yok, tank bakiyesi hareketlerden gelir.

**GreenOps:** tank basina geometri (ic cap, silindir yuksekligi, alt ve ust konik acisi, cetvel ofseti, posa
borusu); cetvel mm'den litre, sabit yogunlukla kg (Excel'den alinmis formul).

**Veri:**
- `storage_unit_geometries` (tank basina en fazla bir): `shape` (`vertical_cylinder`), `inner_diameter_mm`,
  `cylinder_height_mm`, `bottom_cone_deg`, `top_cone_deg` (0 = duz), `ruler_offset_mm`, `dead_volume_l`
  (posa borusu vb.). Cetvel dipten dolu yuksekligi olcer (kullanici cevabi); ofset cetvelin sifiri ile tank dibi
arasindaki fark.
- `tank_level_readings`: tank, olculen mm, hesaplanan litre ve kg, kullanilan yogunluk, o anki defter bakiyesi,
  fark, olcen, not, zaman.

**Hesap:** alt koni + silindir + ust koni hacimleri, olculen yukseklige kadar (kesik koni formulleri); yogunluk
tanktaki urun tipinin yogunlugu (yoksa urun varsayilani). Kapasiteyi asan ya da negatif olcum reddedilir. Birim
testi GreenOps Excel'inden alinan birkac tank/olcum ornegiyle (kullanici ornek verirse).

**Defterle baglanti:** okumada defter bakiyesi ile fark gosterilir; kullanici isterse fark icin sayim duzeltmesi
(`adjustment`) tek tikla acilir (ayri onay). Okuma tek basina stoku degistirmez.

**API:** `PUT/GET /api/storage-units/{id}/geometry`, `POST /api/storage-units/{id}/level-readings` (hesaplar,
kaydeder), `GET .../level-readings`; yetki `storage_unit.measure`.

**Flutter:** tank ayarlarinda geometri formu; tank sayfasinda "Seviye olc" (mm gir -> litre, kg, defterle fark;
"Farki duzelt"); okuma gecmisi.

## 5. QR etiket ve izlenebilirlik sayfasi

Iki ayri QR:

**a) Ic etiket (fabrika icinde, istege bagli):** teslimat, gruplari ve tank icin QR'li etiket. QR yalnizca
uygulamanin okuyacagi bir kod tasir (`olv:batch:<id>`, `olv:tank:<id>`); disariya bilgi vermez. Etiket basmak
istege baglidir: etiket penceresinde "teslimat etiketi" ve "grup etiketleri" ayri ayri secilir (ikisi, biri ya da
hicbiri; secim cihazda hatirlanir).
- Teslimat QR'i okutulunca **teslimat durumu** acilir: teslimat (kg, tedarikci, tarih), hangi gruplara bolundugu
  (grup kodu, kg) ve her grubun su anki asamasi (asama adi, durum, ilerleme "5/9"); gruba dokununca grup.
  Grup QR'i okutulunca dogrudan grubun asama sayfasi.
- API: teslimat durumu icin `GET /api/production-batches/{id}/overview` (teslimat + gruplar + guncel asama).
- API: `GET /api/production-batches/{id}/label.pdf`, `GET /api/storage-units/{id}/label.pdf` (A6 / etiket
  yazicisi boyutu; kod, tedarikci, kg, tarih, QR). PDF sunucuda (OpenPDF + ZXing).
- Flutter: parti ve tank sayfasinda "Etiket" (PDF'i paylas / yazdir); ana ekranda "QR okut" (kamera) -> ilgili
  sayfa.

**b) Sise QR'i ve herkese acik sayfa:** dolum kosusu (lot) basina tahmin edilemez bir anahtar
(`public_token`, 22+ karakter); QR adresi `https://<alan-adi>/t/<token>`.
- Sayfa sunucuda basit HTML (Spring MVC + sablon), giris gerektirmez, hiz sinirli. Gosterilenler yalnizca secili
  alanlar: urun, lot, dolum tarihi, cesit, hasat donemi (girdilerin teslimat tarih araligi), asitlik (kosu
  degerleri), tedarikci ve tarla (kullanici karari: gosterilir). Musteri, fiyat, kullanici, tank adi
  gosterilmez.
- Icerik soy agacindan derlenir (kosu -> tank materyali -> tanka giren partiler); tank defteri sayesinde
  "bu tanka giren partiler" kosunun cekis anina kadar sinirlanir.
- Token iptal edilebilir / yenilenebilir (`finished_product_run.manage`).
- Flutter: kosu detayinda "Sise QR'i" (QR goruntusu, adres, PDF etiket).
- GreenOps'taki aciklar tekrarlanmaz: kiraci/ID tahmini yok (token), hiz siniri gercek istemci IP'siyle (nginx
  `X-Forwarded-For` yalnizca guvenilen proxy'den), yalnizca okuma.

**Alan adi:** henuz yok (kullanici). Adres tabani ayardan gelir (`app.public-base-url`, varsayilan sunucu IP'si);
sayfa sabit bir sablonla ve ustte "Bu sayfa deneme amaclidir" uyarisiyla yayinlanir. Sisede kalici QR basilmadan
once alan adi ve HTTPS kesinlesmeli; ayar degisince QR'lar yeni adresle uretilir.

## Adimlar

1. API: alim fiyati ve kanal, yetki, tedarikci alim ozeti; testler.
2. API: dolum firesi; testler.
3. API: KPY hesaplanan attribute, pirina/zeytin tanimlari, IAS asamasi seed'i, demo veri; testler.
4. API: tank geometrisi ve seviye okumalari, hacim hesabi (birim testleri); testler.
5. API: etiket PDF'leri, kosu public token'i, herkese acik sayfa ve hiz siniri; testler.
6. Flutter: 1-2 (teslimat formu, tedarikci ozeti, kosuda fire).
7. Flutter: 3-4 (IAS ozeti, seviye olcumu ve geometri).
8. Flutter: 5 (etiketler, QR okutma, sise QR'i).
9. Deploy (kullanici istediginde; alan adi ve HTTPS ayri is).

Her adimdan sonra rapor, commit kullanici onayiyla.

## Kararlar (2026-11-24)

1. Fiyat yetkisi admin, planner ve food_engineer'da; operator rolleri gormez.
2. Fiyat KDV haric girilir, KDV orani tutulur; tutarlar KDV haric ve dahil gosterilir. TL.
3. Pirina olcumu ayri, istege bagli "IAS Pirina Olcumu" asamasi (Sikim'dan sonra); zeytin icin "IAS Zeytin
   Olcumu" (Sikim'dan once).
4. Cetvel dipten dolu yuksekligi olcer. Tank olculeri sonra girilir.
5. Sise sayfasinda tedarikci ve tarla gosterilir.
6. Alan adi yok: ayarli adres tabani ve "deneme" uyarili sabit sablon.
7. QR istege bagli; teslimat ve grup etiketleri ayri secilir; teslimat QR'i teslimat durumunu (gruplar ve
   asamalari) acar.
