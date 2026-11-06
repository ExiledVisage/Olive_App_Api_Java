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

## Davranış farkları

### 1. `Accept: application/json` olmadan yetkisiz istek
- **Laravel:** Token yoksa ve `Accept` başlığı JSON değilse `login` route'una yönlendirmeye çalışıyor, route olmadığı için **500 HTML** dönüyor.
- **Java:** Her zaman `401 {"message": "Unauthenticated."}` (Faz 2).
- **Neden:** Laravel'deki davranış bir hata; frontend her zaman `Accept: application/json` gönderiyor.

### 2. Bozuk JSON gövdesi
- **Laravel:** Gövdeyi boş sayıp validation'a devam ediyor (zorunlu alanlar için "required" hataları).
- **Java:** `400 {"message": "Malformed JSON."}`
- **Neden:** Java'da gövde okunamadan validation çalışamaz. Frontend bozuk JSON göndermiyor.

### 3. Tek tek `@RequestParam` ile zorunlu query parametreleri
- **Laravel:** Bütün eksik parametreler tek cevapta: `"The from field is required. (and 1 more error)"`.
- **Java:** `@RequestParam` ilk eksikte durur ve tek hata verir.
- **Çözüm (fark değil, kural):** Zorunlu query parametreleri bir record'da toplanıp `@Valid @ModelAttribute` ile alınır. snake_case adlar `@BindParam("customer_id")` ile bağlanır. Böylece hatalar Laravel'deki gibi birlikte döner. `@RequestParam` sadece isteğe bağlı parametreler için kullanılır.

### 4. Bir alanda birden fazla kural hatası
- **Laravel:** Kurallar yazıldığı sırada değerlendirilir, mesajlar o sırada gelir.
- **Java:** Alanlar DTO'daki tanım sırasına göre sıralanır (Laravel'le aynı sıra yazılmalı). Aynı alandaki birden fazla Bean Validation hatasının sırası garanti değildir.
- **Neden:** Hibernate Validator constraint sırasını garanti etmiyor. Böyle bir durum çıkarsa (tek alanda iki hata), o kontrol elle yazılır ve `LaravelValidationException` ile fırlatılır (`Listing.perPage()` örneği).

### 5. Nginx'in kendi cevapları
- **Laravel:** Kök yola (`/`) desteklenmeyen metotla gelen istek Laravel'e ulaşmadan nginx tarafından `405` HTML ile cevaplanıyor.
- **Java:** `404 {"message": "The route / could not be found."}`
- **Neden:** API dışı; `/api` altında olmayan yollar frontend tarafından kullanılmıyor.
