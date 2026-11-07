# Laravel'den Bilinen Farklar

Java tarafı Laravel'in API sözleşmesini birebir kopyalar. Bu dosya, birebir kopyalanmayan
ya da kopyalanamayan davranışları listeler. Her madde için: Laravel ne yapıyor, Java ne
yapıyor, neden fark kabul edildi.

Yeni bir fark ortaya çıkarsa buraya yazılır. Buraya yazılmamış her fark bir hatadır.

## Cevap formatı (JSON'u etkilemeyenler)

Bunlar `reference/compare.py` tarafından fark sayılmaz, çünkü JSON düzeyinde aynıdırlar.

| Konu | Laravel | Java |
|---|---|---|
| `/` kaçışı | `"http:\/\/localhost"` | `"http://localhost"` |
| Unicode | `"Çankaya"` | `"Çankaya"` |
| Hata gövdesi biçimi | 403/404/405 cevapları girintili (pretty print) | Tek satır |
| JSON `Content-Length` | Yok (önündeki nginx JSON'u chunked gönderiyor) | Var. APK indirmelerinde iki tarafta da var ve aynı |

## Laravel hataları (Java'da düzeltildi)

Laravel'de 500 veren ya da veriyi bozan davranışlar. Kopyalanmadı; Java'nın davranışı
`IdentityParityIT`'de ayrıca tanımlı.

### 1. API'den izinli rol oluşturma 500 veriyor (Faz 2)
- **Laravel:** `POST /api/roles` rolü `guard_name = 'sanctum'` ile kaydediyor (istek sırasında varsayılan guard `sanctum`). İzinler `web` guard'ında olduğu için `syncPermissions` 500 veriyor; işlem transaction içinde olmadığından rol yine de kaydediliyor. İzinsiz oluşturulan rol de `sanctum` guard'lı kalıyor: kullanıcılara atanamıyor, silinmeye çalışılınca 500.
- **Java:** Roller her zaman `web` guard'ıyla ve tek transaction'da oluşturulur: `201`, izinler atanmış. Silme `200`.
- **Etki:** Laravel'de bu yolla oluşmuş `sanctum` guard'lı roller canlı veritabanında varsa (Faz 12 şema kontrolünde bakılmalı) Java onları kullanıcıya atayamaz.

### 2. Login'e e-posta yerine dizi göndermek 500 veriyor (Faz 2)
- **Laravel:** `{"email": ["a@b.c"]}` → rate limiter diziyi metne çeviremiyor → 500.
- **Java:** `422`, "The email field must be a string." ve "must be a valid email address." hataları.

### 3. Admin kullanıcıyı `roles: null` ile güncellemek 500 veriyor (Faz 2)
- **Laravel:** `PUT /api/users/{admin}` gövdesinde `"roles": null` → `in_array(null)` TypeError → 500. Ayrıca rol kontrolü düşse bile ad/e-posta değişikliği önceden kaydedilmiş oluyor (transaction yok).
- **Java:** `422 {"message": "Admin kullanicisindan admin rolu kaldirilamaz."}` ve hiçbir değişiklik kaydedilmez.

### 4. Attribute eşlemesi eklemek her zaman 500 veriyor (Faz 4)
- **Laravel:** `POST /api/production-products/{id}/attribute-definitions` → `StoreProductionProductAttributeDefinitionRequest`'teki `unique` kuralı Query Builder'da olmayan `whereHas`'ı çağırıyor → her istekte 500. API'den hiçbir kategoriye attribute eklenemiyor (demo verideki eşlemeler seeder'dan).
- **Java:** Amaçlanan davranış: anahtar bu kategoride zaten eşlenmişse 422 "The attribute key has already been taken.", değilse global tanım `firstOrCreate` ile bulunur/oluşur, eşleme oluşur (200, Laravel'in `->fresh()` ile döndüreceği gibi).

### 5. Attribute eşlemesi güncellemede `attribute_key` 500 veriyor (Faz 4)
- **Laravel:** `UpdateProductionProductAttributeDefinitionRequest`'teki `unique` kuralı eşleme tablosunda olmayan `attribute_key` kolonuna bakıyor → SQL hatası, 500. Servis anahtarı zaten değiştirmiyor.
- **Java:** Anahtar bu kategoride başka bir eşlemede varsa 422, yoksa yok sayılır (diğer alanlar güncellenir).

### 6. Proses alanı ve proses tipi `unique` kuralları yanlış route parametresine bakıyor (Faz 4)
- **Laravel:** FormRequest'ler `route('processType')` kullanıyor, route parametresinin adı `{process_type}` → null. Sonuçlar:
  - Aynı proseste tekrar eden `field_key`: `unique` kuralı yakalamıyor, veritabanının unique kısıtı 500 veriyor.
  - Proses tipini **kendi adıyla** güncellemek: `ignore()` kendi kaydını görmüyor → 422 "The name has already been taken."
- **Java:** Tekrar eden anahtar 422 "The field key has already been taken."; kendi adıyla güncelleme 200.

### 7. `Accept: application/json` olmadan yetkisiz istek
- **Laravel:** Token yoksa ve `Accept` başlığı JSON değilse `login` route'una yönlendirmeye çalışıyor, route olmadığı için **500 HTML** dönüyor.
- **Java:** Her zaman `401 {"message": "Unauthenticated."}`.
- **Neden:** Frontend her zaman `Accept: application/json` gönderiyor.

## Davranış farkları

### 8. Bozuk JSON gövdesi (yalnızca `@RequestBody` kullanan endpoint'ler)
- **Laravel:** Gövdeyi boş sayıp validation'a devam ediyor.
- **Java:** `RequestInput` + `LaravelValidator` kullanan endpoint'lerde aynı (bütün Faz 2 endpoint'leri). `@RequestBody` kullanan bir endpoint olursa orada `400 {"message": "Malformed JSON."}`.
- **Kural:** Laravel'de FormRequest ile doğrulanan her endpoint `RequestInput` + `LaravelValidator` kullanır; Bean Validation (`@Valid`) kullanılmaz. Bean Validation ile Laravel'in kural sırası, çoklu hataları ve `unique`/`exists` davranışı kopyalanamıyor.

### 9. Rate limit sayaçları geçiş döneminde ayrı
- **Laravel:** Sayaçlar Laravel'in `cache` tablosunda.
- **Java:** Bellekte (tek uygulama örneği). Geçiş sırasında login Java'ya yönlendirildiği için sayaçlar zaten tek yerde; yönlendirme geri alınırsa sayaçlar sıfırdan başlar.
- **Not:** Java birden fazla örnekle çalışacaksa sayaçlar ortak bir depoya (Redis) taşınmalı.

### 10. E-posta doğrulaması (yakınsama)
- **Laravel:** `egulias/email-validator` RFCValidation.
- **Java:** `EmailRule`: Laravel'de gözlenen davranışlarla aynı (`abc` geçersiz, `a@b` geçerli, uzunluk sınırı yok). Tırnaklı yerel kısım (`"a b"@c.d`), yorum (`a(x)@b`) gibi nadir RFC biçimleri desteklenmez.

### 10b. Tarih doğrulaması (yakınsama, Faz 6)
- **Laravel:** `date` kuralı PHP'nin `strtotime`'ını kullanır: "next monday", "tomorrow", "1 week ago" de geçerli tarih sayılır.
- **Java:** Yalnızca ISO biçimleri (`2026-11-06`, `2026-11-06 10:00[:00]`, `2026-11-06T10:00:00[.ffffff][Z|+03:00]`). Uygulama ve arayüz bu biçimleri gönderiyor; serbest metin 422 alır. Değer, Eloquent'in `datetime` cast'i gibi yazılır: saat dilimi verilmişse çevrilmez, o dilimdeki saat yazılır.
- Aynısı aşama alan değerlerinde (`datetime` tipli proses alanı, Faz 6b): "The value must be a valid datetime." yalnızca ISO dışı metinde.

### 11. Sıra garantisi olmayan listeler
- **Laravel:** Kullanıcının rolleri ve bir rolün izinleri `ORDER BY` olmadan geliyor (sıra rastgele olabilir).
- **Java:** id sırası. `compare.py` bu listeleri sırasız karşılaştırır. (Kullanıcının **bütün izinleri** Laravel'de de id sırasında: Spatie `getAllPermissions()` sıralıyor; Java aynı.)
- Diğer sıralamasız ilişkilerde (örn. tankın attribute kuralları, izinli ürünleri) Java Laravel'le aynı SQL'i çalıştırır, sıra genelde aynı çıkar. Ama geri alınan bir insert'ten sonra yeni satırların fiziksel yeri bağlantıya bağlı: PHP her istekte yeni bağlantı açar, Java havuzdaki bağlantıyı kullanır. `StorageUnitParityIT`'de bu yüzden bir durumda liste id'ye göre karşılaştırılıyor.
- `latest()` (yalnızca `created_at`) aynı saniyede oluşan kayıtlarda sırasız; güncellenen satır tablonun sonuna taşındığı için eşitlikte sıra değişebiliyor. Parity testlerinde bu listeler (aynı saniyede oluşan tarifler, dolum koşuları; bir lotun koşuları; deponun rafları, raf içerikleri, depo stoğu) sırasız karşılaştırılıyor.
- Eşit sıralama anahtarı da sırasız: `ORDER BY order_date desc` aynı günlü satırları PostgreSQL'in sorgu planına göre dizer (günlük özetin "bugün sevk edilen siparişler"i). Laravel'de de sıra tanımsız; canlı karşılaştırmada 373 günün 15'inde yalnızca bu sıra farklı çıktı, içerik aynı. Müşteri adresleri (`latest()`) parity testinde sırasız.

### 12. Nginx'in kendi cevapları
- **Laravel:** Kök yola (`/`) desteklenmeyen metotla gelen istek Laravel'e ulaşmadan nginx tarafından `405` HTML ile cevaplanıyor.
- **Java:** `404 {"message": "The route / could not be found."}`
- **Neden:** API dışı; `/api` altında olmayan yollar frontend tarafından kullanılmıyor.
### 7b. Aşama alan değerlerinde dizi olmayan `fields` ve sayı olmayan alan id'si 500 veriyor (Faz 6b)
- **Laravel:** `POST /api/batch-processes/{id}/sync-field-values` isteğinin `withValidator` kancası kurallar düşse de çalışıyor:
  - `"fields": "abc"` → `foreach` uyarısı → 500.
  - `"process_type_field_id": "abc"` → `ProcessTypeField::find('abc')` SQL hatası → 500.
- **Java:** Kanca bu elemanları atlıyor; kural hataları `422` dönüyor ("The fields field must be an array.", "The fields.0.process_type_field_id field must be an integer.").

### 13. Stok hareketlerinde tank kilidi (Faz 5c)
- **Laravel:** Kapasite ve tank uyumu kontrolü transaction içinde ama kilitsiz. Aynı tanka eşzamanlı iki giriş, ikisi de kapasiteyi boş görüp tankı taşırabiliyor.
- **Java:** İşlemdeki tank(lar) transaction başında `SELECT ... FOR UPDATE` ile kilitleniyor (transferde iki tank, id sırasıyla). Girişler sırayla işleniyor, tank taşmıyor. Tek istekli cevaplar aynı. `StorageMovementConcurrencyIT` dört eşzamanlı 60 L girişini 100 L'lik tanka gönderiyor: biri 201, gerisi 422. Kilitsiz sürümde bu test düşüyor.

### 14. Tarif ve bitmiş üründe 500 veren istekler (Faz 8a)
- **Laravel:**
  - Tarifte sayı olmayan oran (`"ratio": "abc"`, `"ratio": [1]`): oran toplamını hesaplayan `withValidator` kancası kurallar düşse de çalışıyor, PHP toplamada `TypeError` → 500.
  - Tarifte aynı ürün tipi ya da aynı attribute iki kez, üründe aynı ambalaj kalemi iki kez: validation geçiyor, unique kısıtı (`fpr_content_unique`, `fpr_attr_rule_unique`, `fp_component_unique`) → 500.
  - Dolum koşusu olmayan ama sipariş kalemi, raf/depo stoğu ya da depo hareketi olan ürünü silmek: restrict FK → 500.
- **Java:**
  - Kanca sayı olmayan oranı 0 sayıyor; alanın kendi hatası (`The contents.0.ratio field must be a number.`) ve toplam mesajı 422 dönüyor.
  - Bu alanlara `distinct` kuralı eklendi: `The contents.1.production_product_type_id field has a duplicate value.` (422).
  - Silmede kullanım yerleri sayılıyor: `Bu son ürün silinemez; kullanıldığı yerler: 4 sipariş kalemi.` (422).

### 15. Hareket geçmişi olan bitmiş ürün rafı ya da deposu silinirken 500 (Faz 8c)
- **Laravel:** Stoğu boşalmış raf ya da depo silinebilir görünüyor, ama `finished_product_stock_movements` rafa ve depoya restrict FK ile bağlı. Bir kez giriş/çıkış görmüş raf ya da depo silinirken FK hatası → 500.
- **Java:** Kullanım yerleri sayılıyor (422): `Bu slot silinemez; kullanıldığı yerler: 2 stok hareketi.`, `Bu depo silinemez; kullanıldığı yerler: 14 stok hareketi. Pasife alabilirsiniz.`

### 16. Müşteri ve adreste `NOT NULL` kolona gelen `null` 500 veriyor (Faz 9a)
- **Laravel:** Kurallar `nullable|boolean` / `nullable|string`, ama kolonlar `NOT NULL`: müşteri güncellemesinde `"is_active": null`, adres eklemede `"is_default": null`, adres güncellemesinde `"is_default": null` ya da `"country": null` → SQL hatası → 500. (Müşteri ve adres eklemede `is_active`/`country` için `??=` var, orada sorun yok.)
- **Java:** Bu kolonlara gelen `null` "değiştirme" sayılıyor: güncellemede alan yazılmıyor, adres eklemede kolon varsayılanı (`is_default = false`) kalıyor; cevap 200/201.

## Kopyalanan ama bilinmesi gereken Laravel davranışları

Farklı değil, birebir aynı; ama kapatılması düşünülebilecek davranışlar (karar Laravel kapandıktan sonra).

- **Attribute eşleme route'ları izin istemiyor (Faz 4):** `production-products/{id}/attribute-definitions` altındaki listeleme, ekleme, güncelleme ve silme yalnızca giriş istiyor; giriş yapan her kullanıcı bir kategorinin attribute tanımlarını değiştirebilir/silebilir.
- **Uzantı senkronizasyonu aşamanın hattını kontrol etmiyor (Faz 4):** `PUT production-lines/{line}/stages/{stage}/extensions` başka bir hattın aşamasını da kabul ediyor.
- **Veritabanı kısıtına takılan istekler 500 (Faz 4):** ürün tipinde `unit` için yalnızca `KG`/`LITRE` geçerli ama validation bunu denetlemiyor; `NOT NULL` kolona açıkça `null` (örn. aşamada `"is_required": null`) 500.
- **Tanktaki materyali azaltan aşamalar tank bakiyesini güncellemiyor (Faz 6b):** "Ön Dinlenme"de yağ önce tanka girer, aynı aşamanın dönüşümü (çökelme) tanktaki bu materyalin miktarını düşürür. "Filtreleme" tartımı da tanktaki yağın miktarını düşürür. İkisi de `storage_unit_balances`'ı yeniden hesaplamıyor ve stok hareketi yazmıyor. Sonuçta tank bakiyesi (ör. 899,75 L) tanktaki materyallerin toplamından (879,75 L) büyük kalıyor ve fark hareket geçmişinde görünmüyor. Java birebir kopyalıyor; geçiş döneminde iki uygulama aynı veritabanını kullandığı için farklı davranamaz.
- **Raf güncellemesi rafın deposunu kontrol etmiyor (Faz 8c):** `PATCH finished-product-storages/{depo}/slots/{raf}` başka bir deponun rafını da güncelliyor (route binding kapsamsız). Silmede de aynı.
