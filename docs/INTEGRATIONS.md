# Dis yazilim entegrasyonlari (uzak hedef)

Durum: not (2026-11-24), plan degil, kod yok. Kullanici: "entegrasyonlar simdilik uzak konseptler, hemen gerek yok".
GreenOps'ta WooCommerce entegrasyonu var (HMAC'li webhook ile siparis).

## Istenenler

| Grup | Ornekler | Akis |
|---|---|---|
| Pazar yeri / e-ticaret | Trendyol, Hepsiburada, WooCommerce | Siparisler bize gelir; stok ve fiyat oraya gider |
| On muhasebe / ERP | Parasut, Mikro, Logo | Satis faturasi, cari ve stok hareketi bizden onlara gider |

## Ortak altyapi (dusunce)

- `integration` modulu; saglayici basina baglayici (adapter), ortak arayuz: siparis cek, stok/fiyat gonder,
  fatura/cari gonder.
- Eslestirme tablolari: bizim urun (finished product) <-> saglayici SKU / barkod; musteri <-> cari.
- Erisim bilgileri sifreli saklanir (sunucu anahtariyla), kodda ve depoda durmaz.
- Giden islemler icin outbox + tekrar deneme; senkron gecmisi ve hata ekrani.
- Gelen siparisler mevcut siparis modulune (sales) duser; kaynak saglayici ve dis siparis no tutulur.

## On kosullar

- Fatura ve cari modulu yok: muhasebe entegrasyonundan once en az satis faturasi kaydi gerekir.
- Urunlerde barkod / SKU alani ve pazar yeri fiyati.
- Dogal sira: once pazar yeri siparis + stok (WooCommerce, Trendyol, Hepsiburada), sonra muhasebe (Parasut REST;
  Mikro ve Logo kurulum tipine gore).

Zamani gelince ayri, ayrintili plan yazilir.
