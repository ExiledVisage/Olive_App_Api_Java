"""Faz 6b (prosesler) icin Laravel'in davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/batch-process.json (Java'da BatchProcessParityIT).

  python -I reference/capture_batch_process.py <laravel_compose_dir> [base_url]

Hat 1'de yeni bir parti acilir ve 12 asamasi sirayla yurutulur:
  1 Kantarda Tartim (weighing)       2 Ayiklama                3 Ikinci Tartim (weighing)
  4 Sikim Unitesine Transfer         5 Sikim (transformation)  6 Zeytinyagi Tartimi (weighing)
  7 Pirina Tartimi (weighing)        8 Asitlik Analizi (analysis)
  9 On Dinlenme (storage inbound + transformation; onayli)    10 Filtreleme (weighing)
 11 Tanklarda Toplama (storage transfer; onayli)              12 Randiman Tespiti (son asama; onayli)
Seed'de boolean/datetime/json alan yok: Randiman'in proses tipine (12) SQL ile uc alan eklenir.
Kullanicilar: admin (her yetki), operator1 (tamamlama/atlama, geri acma ve onay yok),
planner (yalnizca goruntuleme), depo (proses yetkisi yok).
"""
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("laravel_capture", HERE / "laravel_capture.py")
lc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(lc)

cap = lc.Capture(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8001")
c = cap.case
P = "/api/batch-processes"
OPERATOR = "operator1@olivamore.com"
PLANNER = "planner@olivamore.com"
DEPO = "depo@olivamore.com"


def done(name, n, body=None, **kw):
    """Asamayi tamamla."""
    return c(name, "PATCH", P + "/{P%d}" % n, dict({"status": "completed"}, **(body or {})), **kw)


# ---- hazirlik ----------------------------------------------------------
cap.sql("alan boolean", "insert into process_type_fields (process_type_id, field_key, label, data_type, is_required, sort_order,"
        " is_active, created_at, updated_at) values (12, 'numune_alindi', 'Numune Alındı', 'boolean', false, 2, true, now(), now())"
        " returning id", save="FB")
cap.sql("alan datetime", "insert into process_type_fields (process_type_id, field_key, label, data_type, is_required, sort_order,"
        " is_active, created_at, updated_at) values (12, 'olcum_zamani', 'Ölçüm Zamanı', 'datetime', false, 3, true, now(), now())"
        " returning id", save="FD")
cap.sql("alan json", "insert into process_type_fields (process_type_id, field_key, label, data_type, is_required, sort_order,"
        " is_active, created_at, updated_at) values (12, 'detay', 'Detay', 'json', false, 4, true, now(), now())"
        " returning id", save="FJ")
c("parti", "POST", "/api/production-batches", {"batch_code": "T-BP-1", "input_production_product_type_ids": [1],
                                               "production_line_id": 1, "supplier_id": 1}, ("B", "data.id"))
for i in range(1, 13):
    cap.sql("proses %d" % i, "select id from batch_processes where production_batch_id = {B} and production_stage_id = %d" % i,
            save="P%d" % i)
cap.sql("eski proses 9", "select id from batch_processes where production_batch_id = 273 and production_stage_id = 9", save="OLD9")
cap.sql("eski proses 1", "select id from batch_processes where production_batch_id = 273 and production_stage_id = 1", save="OLD1")
cap.sql("eski materyal", "select id from materials where production_batch_id = 273 order by id limit 1", save="OLDM")

# ---- goruntuleme -------------------------------------------------------
c("goster bekleyen", "GET", P + "/{P1}")
c("goster tamamlanmis", "GET", P + "/{OLD9}")
c("goster tamamlanmis tartim", "GET", P + "/{OLD1}")
c("goster olmayan", "GET", P + "/999999")
c("goster planlamaci", "GET", P + "/{P1}", as_user=PLANNER)
c("goster yetkisiz", "GET", P + "/{P1}", as_user=DEPO)

# ---- sira ve dogrulama -------------------------------------------------
c("sira atlanamaz", "PATCH", P + "/{P2}", {"status": "in_progress"})
c("bilgi guncelle", "PATCH", P + "/{P1}", {"note": "ilk not", "operator_id": 2, "started_at": "2026-11-07 08:00"})
c("bilgi hatali", "PATCH", P + "/{P1}", {"status": "bogus", "operator_id": 999, "started_at": "dun", "note": 5,
                                         "operation_data": "x"})
done("tamamla veri yok", 1)
done("tamamla hatali", 1, {"operation_data": {"quantity": 0, "unit": "TON", "production_product_type_id": 999}})
done("tamamla zorunlu alan", 1, {"operation_data": {"quantity": 5000}})
done("tamamla planlamaci", 1, {"operation_data": {"quantity": 5000}}, as_user=PLANNER)

# ---- alan degerleri ----------------------------------------------------
c("alan bos", "POST", P + "/{P1}/sync-field-values", {})
c("alan baska tip", "POST", P + "/{P1}/sync-field-values", {"fields": [{"process_type_field_id": 3, "value": "x"}]})
c("alan hatali", "POST", P + "/{P2}/sync-field-values", {"fields": [{"process_type_field_id": 3, "value": "abc"},
                                                                     {"process_type_field_id": 999}, {"value": 1}]})
c("alan doldur", "POST", P + "/{P1}/sync-field-values", {"fields": [{"process_type_field_id": 1, "value": "16 ABC 123"},
                                                                     {"process_type_field_id": 2, "value": 4711}]})
c("alan sil", "POST", P + "/{P1}/sync-field-values", {"fields": [{"process_type_field_id": 1, "value": "16 ABC 124"}]})
c("alan planlamaci", "POST", P + "/{P1}/sync-field-values", {"fields": [{"process_type_field_id": 1, "value": "x"}]},
  as_user=PLANNER)

# ---- tartim (asama 1) --------------------------------------------------
done("tartim birim", 1, {"operation_data": {"quantity": 5000, "unit": "LITRE"}})
done("tartim baska urun", 1, {"operation_data": {"quantity": 5000, "production_product_type_id": 3}})
done("tartim", 1, {"note": "tartildi", "operation_data": {"quantity": "5000.5", "unit": "KG", "foo": "bar"}})
done("tartim tekrar", 1, {"note": "degismez"})
c("tamamlanmis alan", "POST", P + "/{P1}/sync-field-values", {"fields": [{"process_type_field_id": 1, "value": "x"}]})
c("geri al yan etki", "PATCH", P + "/{P1}", {"status": "waiting"})
c("geri al yetkisiz", "PATCH", P + "/{P1}", {"status": "waiting"}, as_user=OPERATOR)
c("atla yan etki", "PATCH", P + "/{P1}", {"status": "skipped"})

# ---- asama 2 (uzantisiz) -----------------------------------------------
c("baslat planlamaci", "PATCH", P + "/{P2}", {"status": "in_progress"}, as_user=PLANNER)
c("birak planlamaci", "PATCH", P + "/{P2}", {"status": "waiting"}, as_user=PLANNER)
c("atla zorunlu", "PATCH", P + "/{P2}", {"status": "skipped"})
c("atla yetkisiz", "PATCH", P + "/{P2}", {"status": "skipped"}, as_user=PLANNER)
c("basarisiz", "PATCH", P + "/{P2}", {"status": "failed", "completed_at": "2026-11-07 09:30:15"})
c("basarisiz alan", "POST", P + "/{P2}/sync-field-values", {"fields": [{"process_type_field_id": 3, "value": "2.5"}]})
done("uzantisiz tamamla", 2, {"operation_data": {"x": 1, "y": [1, 2.50], "z": {"a": None}}})
c("baslat tamamlanmis", "PATCH", P + "/{P2}", {"status": "in_progress"})

# ---- ikinci tartim (3), transfer (4) -----------------------------------
done("tartim artiramaz", 3, {"operation_data": {"quantity": 6000}})
done("ikinci tartim", 3, {"operation_data": {"quantity": 4800}})
done("veri yok", 4)

# ---- sikim (5) ---------------------------------------------------------
done("sikim cikti yok", 5, {"operation_data": {"input_quantity": 4800}})
done("sikim cikti urunu yok", 5, {"operation_data": {"input_quantity": 4800, "outputs": [{"quantity": 10}, 5]}})
done("sikim fazla girdi", 5, {"operation_data": {"input_quantity": 4801,
                                                 "outputs": [{"product_type_key": "olive_oil", "quantity": 900}]}})
done("sikim kutle", 5, {"operation_data": {"input_quantity": 4800, "outputs": [
    {"product_type_key": "olive_oil", "quantity": 900}, {"production_product_id": 3, "quantity": 5000}]}})
done("sikim bilinmeyen urun", 5, {"operation_data": {"input_quantity": 4800,
                                                     "outputs": [{"product_type_key": "fruit_additive", "quantity": 1}]}})
done("sikim baska cesit", 5, {"operation_data": {"input_quantity": 4800,
                                                 "outputs": [{"production_product_type_id": 4, "quantity": 1}]}})
done("sikim birim", 5, {"operation_data": {"input_quantity": 4800,
                                           "outputs": [{"product_type_key": "olive_oil", "quantity": 900, "unit": "KG"}]}})
done("sikim", 5, {"operation_data": {"input_quantity": 4800, "outputs": [
    {"product_type_key": "olive_oil", "quantity": 900.25}, {"production_product_id": 3, "quantity": 3500, "unit": "KG"}]}})

# ---- tartimlar (6, 7) --------------------------------------------------
done("yag tartimi fazla", 6, {"operation_data": {"quantity": 1000}})
done("yag tartimi", 6, {"operation_data": {"quantity": 899.75}})
done("pirina tartimi", 7, {"operation_data": {"quantity": "3400"}})

# ---- analiz (8) --------------------------------------------------------
done("analiz bilinmeyen", 8, {"operation_data": {"attributes": [{"attribute_key": "nope", "value": 1}]}})
done("analiz tanim yok", 8, {"operation_data": {"attributes": [{"production_product_attribute_definition_id": 999}]}})
done("analiz baska kategori", 8, {"operation_data": {"attributes": [{"production_product_attribute_definition_id": 1,
                                                                       "value": 1}]}})
done("analiz baska parti", 8, {"operation_data": {"material_id": "{OLDM}", "attributes": [{"attribute_key": "acidity",
                                                                                               "value": 1}]}})
done("analiz bos", 8, {"operation_data": {"attributes": [{}]}})
done("analiz", 8, {"operation_data": {"attributes": [
    {"attribute_key": "acidity", "value": "0.31"},
    {"attribute_key": "cultivar", "value_text": "Gemlik"},
    {"attribute_key": "vitamin_profile", "value": {"e": 12}},
    {"production_product_attribute_definition_id": 7, "value_json": [1, 2]}]}})

# ---- on dinlenme (9): tanka giris + cokelme, onayli ------------------
done("dinlenme veri yok", 9)
done("dinlenme sabit tur", 9, {"operation_data": {"action": "transfer", "quantity": 100, "from_storage_unit_id": 7,
                                                  "to_storage_unit_id": 8, "input_quantity": 10,
                                                  "outputs": [{"production_product_id": 3, "quantity": 5}]}})
done("dinlenme tank cesidi", 9, {"operation_data": {"quantity": 899.75, "storage_unit_id": 13, "input_quantity": 20,
                                                    "outputs": [{"production_product_id": 3, "quantity": 15}]}})
done("dinlenme kapasite", 9, {"operation_data": {"quantity": 899.75, "storage_unit_id": 1, "input_quantity": 20,
                                                 "outputs": [{"production_product_id": 3, "quantity": 15}]}})
done("dinlenme gonder", 9, {"operation_data": {"quantity": 899.75, "storage_unit_id": 7, "input_quantity": 20,
                                               "outputs": [{"production_product_id": 3, "quantity": 15.5}]}})
c("onay bekleyen alan", "POST", P + "/{P9}/sync-field-values", {"fields": [{"process_type_field_id": 1, "value": "x"}]})
c("onay bekleyen durum", "PATCH", P + "/{P9}", {"status": "in_progress"})
c("onay yetkisiz", "POST", P + "/{P9}/approve", {}, as_user=OPERATOR)
c("red gerekce yok", "POST", P + "/{P9}/reject", {})
c("red", "POST", P + "/{P9}/reject", {"note": "Miktar yanlış"})
c("onay beklemiyor", "POST", P + "/{P9}/approve", {})
c("red beklemiyor", "POST", P + "/{P9}/reject", {"note": "x"})
done("dinlenme tekrar gonder", 9, {"operation_data": {"quantity": 899.75, "storage_unit_id": 7, "input_quantity": 20,
                                                      "outputs": [{"production_product_id": 3, "quantity": 15.5}]}},
     as_user=OPERATOR)
c("onay not uzun", "POST", P + "/{P9}/approve", {"note": "x" * 2001})
c("onay", "POST", P + "/{P9}/approve", {"note": "Tamam"})
c("onayli geri al", "PATCH", P + "/{P9}", {"status": "waiting"})

# ---- filtreleme (10), toplama (11): tanklar arasi transfer -----------
done("filtreleme", 10, {"operation_data": {"quantity": 870}})
done("toplama ayni tank", 11, {"operation_data": {"quantity": 870, "from_storage_unit_id": 7, "to_storage_unit_id": 7}})
done("toplama", 11, {"operation_data": {"quantity": 870, "from_storage_unit_id": 7, "to_storage_unit_id": 8,
                                        "note": "toplandi"}})
c("toplama onay", "POST", P + "/{P11}/approve", {})

# ---- randiman (12): son asama, onayli; alan tipleri ------------------
c("alan tipleri hatali", "POST", P + "/{P12}/sync-field-values", {"fields": [
    {"process_type_field_id": 8, "value": "abc"}, {"process_type_field_id": "{FB}", "value": "yes"},
    {"process_type_field_id": "{FD}", "value": "dun"}, {"process_type_field_id": "{FJ}", "value": "x"}]})
c("alan tipleri", "POST", P + "/{P12}/sync-field-values", {"fields": [
    {"process_type_field_id": 8, "value": "17.83"}, {"process_type_field_id": "{FB}", "value": "1"},
    {"process_type_field_id": "{FD}", "value": "2026-11-07T10:30:00+03:00"},
    {"process_type_field_id": "{FJ}", "value": {"a": 1, "b": [1, 2]}}]})
c("alan tipleri 2", "POST", P + "/{P12}/sync-field-values", {"fields": [
    {"process_type_field_id": 8, "value": 18}, {"process_type_field_id": "{FB}", "value": False},
    {"process_type_field_id": "{FD}", "value": None}, {"process_type_field_id": "{FJ}", "value": []}]})
c("onceki geri al sonraki baslamis", "PATCH", P + "/{P10}", {"status": "waiting"})
done("son asama gonder", 12)
c("son asama onay", "POST", P + "/{P12}/approve", {})
c("parti tamamlandi", "GET", "/api/production-batches/{B}")
c("geri ac planlamaci", "PATCH", P + "/{P12}", {"status": "waiting"}, as_user=PLANNER)
c("geri ac", "PATCH", P + "/{P12}", {"status": "waiting", "note": "yeniden"})
c("geri al sonraki baslamis", "PATCH", P + "/{P11}", {"status": "waiting"})

# ---- atlanabilir asama -------------------------------------------------
cap.sql("asama 12 istege bagli", "update production_stages set is_required = false where id = 12 returning id")
c("atla", "PATCH", P + "/{P12}", {"status": "skipped"})
c("parti atlanmis", "GET", "/api/production-batches/{B}")
cap.sql("asama 12 zorunlu", "update production_stages set is_required = true where id = 12 returning id")

cap.write(HERE.parent / "src/test/resources/laravel/batch-process.json")
