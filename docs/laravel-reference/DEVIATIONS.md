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

### 4. `Accept: application/json` olmadan yetkisiz istek
- **Laravel:** Token yoksa ve `Accept` başlığı JSON değilse `login` route'una yönlendirmeye çalışıyor, route olmadığı için **500 HTML** dönüyor.
- **Java:** Her zaman `401 {"message": "Unauthenticated."}`.
- **Neden:** Frontend her zaman `Accept: application/json` gönderiyor.

## Davranış farkları

### 5. Bozuk JSON gövdesi (yalnızca `@RequestBody` kullanan endpoint'ler)
- **Laravel:** Gövdeyi boş sayıp validation'a devam ediyor.
- **Java:** `RequestInput` + `LaravelValidator` kullanan endpoint'lerde aynı (bütün Faz 2 endpoint'leri). `@RequestBody` kullanan bir endpoint olursa orada `400 {"message": "Malformed JSON."}`.
- **Kural:** Laravel'de FormRequest ile doğrulanan her endpoint `RequestInput` + `LaravelValidator` kullanır; Bean Validation (`@Valid`) kullanılmaz. Bean Validation ile Laravel'in kural sırası, çoklu hataları ve `unique`/`exists` davranışı kopyalanamıyor.

### 6. Rate limit sayaçları geçiş döneminde ayrı
- **Laravel:** Sayaçlar Laravel'in `cache` tablosunda.
- **Java:** Bellekte (tek uygulama örneği). Geçiş sırasında login Java'ya yönlendirildiği için sayaçlar zaten tek yerde; yönlendirme geri alınırsa sayaçlar sıfırdan başlar.
- **Not:** Java birden fazla örnekle çalışacaksa sayaçlar ortak bir depoya (Redis) taşınmalı.

### 7. E-posta doğrulaması (yakınsama)
- **Laravel:** `egulias/email-validator` RFCValidation.
- **Java:** `EmailRule`: Laravel'de gözlenen davranışlarla aynı (`abc` geçersiz, `a@b` geçerli, uzunluk sınırı yok). Tırnaklı yerel kısım (`"a b"@c.d`), yorum (`a(x)@b`) gibi nadir RFC biçimleri desteklenmez.

### 8. Sıra garantisi olmayan listeler
- **Laravel:** Kullanıcının rolleri ve bir rolün izinleri `ORDER BY` olmadan geliyor (sıra rastgele olabilir).
- **Java:** id sırası. `compare.py` bu listeleri sırasız karşılaştırır. (Kullanıcının **bütün izinleri** Laravel'de de id sırasında: Spatie `getAllPermissions()` sıralıyor; Java aynı.)

### 9. Nginx'in kendi cevapları
- **Laravel:** Kök yola (`/`) desteklenmeyen metotla gelen istek Laravel'e ulaşmadan nginx tarafından `405` HTML ile cevaplanıyor.
- **Java:** `404 {"message": "The route / could not be found."}`
- **Neden:** API dışı; `/api` altında olmayan yollar frontend tarafından kullanılmıyor.
