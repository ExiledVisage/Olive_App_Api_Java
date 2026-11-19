# Teslimatı gruplara bölme

Durum: API'de bölme, raporlar, detay alanları, `kind` filtresi, kilo düzeltme ve grup silme yapıldı; elle girilen
kilonun yeniden tartımı ve Flutter kaldı.

Gelen bir zeytin teslimatı (örn. 1000 kg) gruplara bölünür; her grup üretime ayrı zamanda girer. Teslimatın
tamamını tek seferde işlemek de bir gruptur: "Hepsini tekte başlat" kalan kilonun tamamıyla tek grup açar.

```
B-001  teslimat 1000 kg ──[ Kantarda Tartım → Ayıklama → İkinci Tartım → (Transfer) ]
 ├─ B-001-1  300 kg ──[ Transfer → Sıkım → Zeytinyağı Tartımı → … → Randıman Tespiti ]
 ├─ B-001-2  300 kg ──[ … ]
 └─ ayrılmamış 400 kg (hesaplanır)
```

## Veritabanı

Yeni tablo yok. Dev veritabanı sıfırlandığı için ilgili `create` dosyaları düzenlenir.

| Tablo | Sütun | Anlamı |
|---|---|---|
| `production_batches` | `parent_batch_id bigint NULL` (FK aynı tablo, `ON DELETE RESTRICT`, index) | Grubun bağlı olduğu teslimat. Boşsa satır teslimattır. |
| `production_stages` | `uses_machine boolean DEFAULT false NOT NULL` | Aşama makine içerir mi. Seed'de Sıkım ve Filtreleme `true`. |
| `batch_processes` | `inherited_from_process_id bigint NULL` (FK `batch_processes`) | Grupta "ana partide yapıldı" diye geçilen aşama; ana partinin satırını gösterir. |
| `production_batches` | `truck_plate`, `weighing_slip_no` | Teslimatın kamyon plakası (zorunlu) ve kantar fişi. Kantarda Tartım'ın alanları değil, teslimatın sabit bilgisi; gruplar ana partiden okur. |

Ayrı tablo yerine aynı tabloda bağlantı: grubun bugünkü partinin her şeyine ihtiyacı var (aşamalar, materyaller,
girdi/çıktı türleri, raporlar); hepsi `production_batches.id`'ye bağlı ve olduğu gibi çalışır.

Örnek:

| id | batch_code | parent_batch_id | quantity_kg | status |
|---|---|---|---|---|
| 10 | B-001 | NULL | 1000 | in_progress |
| 11 | B-001-1 | 10 | 300 | in_progress |
| 12 | B-001-2 | 10 | 300 | pending |

Ayrılmamış kilo saklanmaz: ana partinin zeytin materyalinin miktarıdır. Bölmede bu materyalden pay ayrılır ve gruba
yeni bir materyal olarak aktarılır (ana materyali teslimatın zeytini); örnekte `1000 − (300 + 300) = 400`. Ana parti
kalanı yeniden tartarsa kalan kendiliğinden güncellenir.

## Aşamaların paylaşımı

Bölme noktası, `stage_order` sırasıyla `uses_machine = true` olan ilk aşamadır. Seed hatlarında (üçü de aynı):

| sıra | aşama | kategori | tür | kimde |
|---|---|---|---|---|
| 1 | Kantarda Tartım | weighing | ölçüm | ana parti |
| 2 | Ayıklama | sorting | geçiş | ana parti |
| 3 | İkinci Tartım | weighing | ölçüm | ana parti |
| 4 | Sıkım Ünitesine Transfer | transfer | geçiş | ana parti ya da grup |
| 5 | Sıkım | machine_process | makine (bölme noktası) | grup |
| 6–11 | Zeytinyağı Tartımı … Randıman Tespiti | | | grup |

- Ölçüm aşaması `process_types.category = 'weighing'`; ayrı işaret yok.
- Geçiş aşaması: makine öncesi, tartım olmayan aşama (Ayıklama, Transfer). Kiloyu ya da yeri değiştirir, ölçmez.
- Ana parti ilk bölmeden sonra kalan kilo için kendi aşamalarına devam eder. Makineli aşamaları hiç yapmaz.

## Kurallar

**Ne zaman bölünür**

- Makine öncesi her an, ama yalnızca ana partinin son tamamlanan aşaması bir tartımsa (ya da kilo oluşturmada
  girildiyse). Ayıklamadan sonra bölmek için önce İkinci Tartım yapılır.
- Ana parti Transfer'i yaptıysa kalan kilo artık bölünmez, tek grup olarak devam eder.
- Grup tekrar bölünmez. Süreç ortasında (makine aşamasından sonra) bölme yok.

**Kilo**

- Oluşturmada kilo girildiyse Kantarda Tartım o kiloyla tamamlanır ve zeytin materyali oluşur (kantarda zorunlu alan
  yok, plaka partide); İkinci Tartım yine zorunlu.
- Kilo elle girilip tartılmadıysa ilk bölmede sorulur: "Teslimatın kilosu tartılmadı. Ayırmadan önce tartmak ister
  misin?" Evet: ana partinin Kantarda Tartım'ı beklemeye alınır, sonucu teslimat kilosu olur.
- Kalan kilo = ana partinin zeytin materyali. Ayırma kalanı aşamaz; "Kalanın hepsi" kısayolu
  var. İki kullanıcı aynı anda bölerse ana parti satırı kilitlenir.
- Grupta bir tartım ayrılan kilodan farklı çıkarsa:
  - eksikse (550) tartım kaydedilir, sorulur: "50 kg ana partiye dönsün mü?" Evet: ana partinin zeytini 50 artar,
    grubun `quantity_kg`'ı 550 olur. Hayır: fark fire.
  - fazlaysa (650) tartım reddedilmez, sorulur: "50 kg ana partiden alınsın mı?" Evet: fark ana partiden gruba
    aktarılır, tartım kaydedilir. Ana partide yetmezse tartım bugünkü gibi reddedilir (tartım miktarı artıramaz).
  - Grup makine aşamasına girene kadar.
  - Uygulama soruyu tartımı tamamlamadan önce sorar; evet derse önce `POST /api/production-batches/{grup}/allocation`
    `{quantity_kg: <tartılan>}` çağrılır (fark teslimata döner ya da ondan alınır, `quantity_kg` aynı farkla değişir),
    sonra tartım tamamlanır. Tamamen ayrılmış teslimata zeytin dönerse kendiliğinden atlanan aşamaları yeniden bekler.
- Teslimat kilosu, ayrılmış toplamın altına indirilemez.

**Grup nereden başlar**

Bölme penceresinde tek soru: "Bu grup nereden başlasın?" Seçenekler ana partide yapılmış makine öncesi aşamalar
(Kantarda Tartım dahil) ve "kaldığı yerden". İkinci Tartım'dan sonra bölününce:

- Kantarda Tartım · Ayıklama · İkinci Tartım · Kaldığı yerden (Transfer)

Seçilenden önceki aşamalar `skipped` + `inherited_from_process_id`; seçilen ve sonrası `waiting`. Aradan atlama yok.
Ana partinin henüz yapmadığı aşamaları grup zorunlu olarak yapar. Grupta tekrarlanan Kantarda Tartım yalnız kiloyu
ölçer; plaka ve fiş teslimatın, ana partiden okunur.

**Grubun aldıkları**

- Kod `B-001-1`, `B-001-2`, … Hat, tedarikçi, tarla, girdi/çıktı türleri ve `received_at` ana partiden kopyalanır.
- `received_at`, tedarikçi, tarla grupta değiştirilemez; ana partide değişince gruplara yazılır.

**Durum**

- Ana partinin durumu kendi aşamaları ve gruplarından türetilir: hiçbiri başlamadıysa `pending`, biri başladıysa
  `in_progress`, kendi aşamaları ve bütün grupları bitip kalan 0 ise `completed`.
- `started_at` ilk başlama, `completed_at` son bitiş.

**Silme**

- Grubu olan ana parti silinmez. Başlamamış grup silinebilir (teslimattan devralınan aşamalar başlamış sayılmaz);
  kilosu kalana döner, teslimatta kendiliğinden atlanan aşamalar yeniden bekler.

## API

| İstek | Açıklama | İzin |
|---|---|---|
| `POST /api/production-batches/{id}/groups` | `{quantity_kg \| all_remaining, start_stage_id \| null, note}` → yeni grup id | `production_batch.create` |
| `GET /api/production-batches/{id}` | ek alanlar: `parent_batch_id`, `parent_batch_code` (grupta), `allocated_kg`, `remaining_kg`, `groups[]` (id, `batch_code`, `quantity_kg`, `status`, `started_at`, `completed_at`, `current_stage` {`production_stage_id`, `name`, `status`}); son üçü yalnız bölünebilen hattaki teslimatta dolu, diğerlerinde `null` | mevcut |
| `GET /api/production-batches` | `kind` filtresi: `delivery` (grup olmayanlar), `processable` (gruplar ve makineli aşaması olmayan hattın partileri), boş = hepsi | mevcut |

| `POST /api/production-batches/{id}/allocation` | grupta: `{quantity_kg}` grubun yeni payı; fark teslimatla paylaşılır; makineli aşamaya girene kadar | `production_batch.update` |
| `DELETE /api/production-batches/{id}` | grupta: başlamamışsa silinir, payı teslimata döner | `production_batch.delete` |

## Raporlar

| Yer | Değişiklik |
|---|---|
| `AnalyticsQueries.batches` | Gelen kilo ana partinin Kantarda Tartım'ından; sıkılan kilo, yağ, pirina gruplardan. Teslimat ve grup bazında randıman. Dönem `received_at` ile. |
| `SupplierDeliveryReadModel` | Yalnız `parent_batch_id IS NULL`; aksi halde kilo çift sayılır. |
| `DailySummaryService` | "Bugün gelenler" teslimatlar; "işlemde olanlar" ana partiler (makine öncesi) ve gruplar. |
| Takvim (`/calendar`) | Teslimat günü sayımı yalnız teslimatlar. |
| `MaterialLineageService` | Grup kodunun yanında teslimat kodu. Teslimat firesi = ana parti + grupların ayıklamaları. |

Materyal: ana partinin zeytini mevcut materyal bölmeyle (ana materyal → çocuk materyal) gruplara pay edilir.

## Flutter

- Model: `parentBatchId`, `parentBatchCode`, `allocatedKg`, `remainingKg`, `groups`. Servis: bölme, `kind` filtresi.
- Parti detayı (`production_process_page.dart`): teslimatta özet (toplam / ayrılan / kalan) ve grup kartları (kod,
  kilo, durum, başlama, şu anki aşama, bekleme süresi); grupta teslimata bağlantı.
- Bölme penceresi: kilo, "Kalanın hepsi", "nereden başlasın", not. Tartım sonrası kilo düzeltme sorusu.
- Aşama ekranında "Makine içerir" anahtarı; hat aşamaları listesinde makine rozeti.
- Listeler: teslimat mı grup mu, raporlardaki ayrıma göre.

## Uygulama sırası

1. ✔ Veritabanı sütunları, seed, bölme servisi ve kuralları, durum hesabı, testler (`BatchGroupsIT`).
2. ✔ API yanıtı (`groups[]`, `remaining_kg`), `kind` filtresi, kilo düzeltme, grup silme. Kalan: elle girilen kilonun
   ilk bölmede yeniden tartılması ve oluşturmada tartılmış teslimatın silinmesi.
3. ✔ Raporlar: analiz (grubun gelen kilosu teslimat kantarından payı), günlük özet, takvim, tedarikçi teslimatları.
   Soyağacında teslimat kodu kaldı.
4. Flutter: model ve servis, detay sayfası ve bölme penceresi, aşama ekranı, listeler.
5. Sonra: bekleyen kilo için uyarı kuralı (örn. "ayrılmamış kilo 24 saati geçti").
