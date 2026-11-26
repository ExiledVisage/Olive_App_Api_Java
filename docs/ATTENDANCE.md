# Giris-cikis (QR ile mesai takibi)

Durum (2026-11-27): API tamam (AttendanceIT); Flutter sirada. Kullanici istegi: iscilerin fabrikaya giris ve
cikisi QR ile kaydedilsin, asil amac mesai ve fazla mesai (ekstra saat) takibi.

Kullanicinin cevaplari:
- Butun iscilerin telefonu ve uygulamada hesabi var.
- Kapida sabit QR olmaz: fotografi cekilip tekrar tekrar kullanilir.
- Okuyucu bekci olabilir (bekci hesabi) ya da kapidaki kamera QR'i algilayabilir.

## Yon: QR iscinin telefonunda, kapida bekci ya da kamera okur

Sabit QR'in kopyalanma sorununu tersine cevirerek cozer: QR kapida degil, iscinin uygulamasindadir ve surekli
degisir; ekran goruntusu bir dakika icinde gecersiz olur.

1. **Giris kartim (isci):** Uygulamada "Giris kartim" sayfasi iscinin kisisel QR'ini gosterir. QR sunucunun
   imzaladigi kisa omurlu koddur (60 sn gecerli, tek kullanimlik); uygulama 30 sn'de bir yeniler. Sayfada ad,
   gecerlilik sayaci ve "Iceridesiniz / Disaridasiniz" durumu.
2. **Kapi okuyucu (bekci):** Yeni rol **Bekci**, izin `attendance.scan`. Okuyucu sayfasi kamerayi surekli acik
   tutar (kiosk modu); okutulan kod icin buyuk sonuc ekrani: "Ahmet Kaya · GIRIS 07:58" (yesil) ya da "CIKIS 17:12"
   (lacivert), gecersiz/suresi dolmus kod kirmizi. Bekci adi gorup kisiyi gozle dogrular.
3. **Giris mi cikis mi:** Sunucu iscinin son kaydina bakar: icerideyse cikis, disaridaysa giris. Ayni kisi 2 dk
   icinde tekrar okutulursa yok sayilir (cift okutma).
4. **Kamera:** Pratik yol kapiya sabitlenen bir tablet: bekci hesabiyla okuyucu sayfasi kiosk modunda acik kalir,
   kamera QR'i kendisi algilar, kimse dokunmaz. Ayni uc kullanilir. IP kamerada QR cozen donanim entegrasyonu
   uzak hedef (docs/INTEGRATIONS.md).

## Mesai ve fazla mesai

Kullanicinin kararlari (2026-11-27):
- **Plan:** fabrika geneli varsayilan Pazartesi-Cuma 10:00-18:00, gunde 8 saat (mola 0); kisiye ozel plan
  tanimlanabilir (kisinin satirlari varsa plani yalniz onlar, satiri olmayan gun izin).
- **Fazla mesai:** planli sureyi asan her dakika (erken gelme dahil); 15 dk'lik dilimlere asagi yuvarlanir
  (`olive.attendance.rounding-minutes`). Plansiz gunde (hafta sonu) calisilanin tamami.
- **Mola:** plana atanir (`break_minutes`) ya da isci okutarak verir (gun icindeki cikis-giris arasi). Dusulen =
  atanan - okutarak verilen (sifirdan kucukse sifir).
- **Gec kalma:** ilk giris - plan baslangici. **Eksik:** gecmis gunde planli - calisilan. **Devamsiz:** planli gunde
  hic kayit yok.
- **Cikis unutulursa:** 16 saatten eski acik giris "cikis yok" sayilir (o gun calisilan 0); yeni okutma giris olur.
  Yonetici cikisi elle girer.
- **Gece vardiyasi:** cikis, girisin gunune yazilir.
- **Fotograf:** okuyucuda simdilik bos profil sablonu (bas harfler); profil fotografi sonra.

## Ekranlar

- **Iceridekiler:** su an iceride kim var, ne zamandan beri (yonetici, bekci).
- **Mesai raporu:** kisi x gun/ay: calisilan, planli, fazla mesai, gec gelme, cikis yok. Excel/CSV disa aktarma.
- **Kendi gecmisim:** iscinin kendi giris-cikislari ve aylik fazla mesaisi.
- **Duzeltme:** yonetici kayit ekler/duzeltir; gerekce zorunlu, kim ne zaman degistirdi saklanir (orijinal silinmez).

## Veri (modul `attendance`, migration V16)

- `attendance_events`: user_id, kind (in/out), occurred_at, source (scan/manual), recorded_by_user_id,
  device_label, note, voided_at/voided_by_user_id/void_reason (duzeltmede eski kayit iptal, yenisi girilir).
- `work_schedules`: user_id (null = fabrika geneli), hafta gunu, baslangic, bitis, mola dk.
- QR kodu tabloda tutulmaz: `OLVATT1.<kullanici>.<son gecerlilik>.<rastgele>.<HMAC>`; sure icinde tekrar okutma
  2 dk'lik cift okutma kuralina takilir, ayrica nonce saklanmaz.

## Uclar

- `GET /api/attendance/my-code` -> {code, expires_at, ttl_seconds, inside, since}
- `POST /api/attendance/scan` {code} -> {user, kind, occurred_at} (izin `attendance.scan`)
- `GET /api/attendance/inside`, `GET /api/attendance/report?from&to&user_id`, `GET /api/attendance/me?from&to`,
  `GET /api/attendance/events?from&to&user_id`, `GET /api/attendance/people` (kisi secicisi)
- `POST /api/attendance/events` (elle, gerekce zorunlu), `POST /api/attendance/events/{id}/void` (gerekce zorunlu)
- `GET|PUT /api/attendance/schedules` (`user_id` bos: fabrika geneli), `DELETE /api/attendance/schedules/users/{id}`

## Yetkiler ve dev verisi

- `attendance.scan` (62) okutma ve iceridekiler, `attendance.view` (63) rapor, kayitlar, plan; `attendance.manage`
  (64) elle kayit, iptal, plan degisikligi. Kendi kodu ve kendi mesaisi icin yetki gerekmez.
- Dev: rol `guard`, kullanici `bekci@olivamore.com` (Kapi Gorevlisi, sifre 123).
- QR imza anahtari `ATTENDANCE_CODE_SECRET`; verilmezse JWT anahtarindan turetilir. Saat dilimi `ATTENDANCE_ZONE`
  (varsayilan Europe/Istanbul).
