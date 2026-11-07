"""Faz 8c (bitmis urun depolari) icin Laravel'in davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/finished-product-storage.json (Java'da FinishedProductStorageParityIT).

  python -I reference/capture_finished_product_storage.py <laravel_compose_dir> [base_url]

Seed'de acikta (hicbir depoya yerlesmemis) sise yok: urun 1, 18 ve 19'un total_stock'u SQL ile artirilir.
Kullanicilar: admin, depo (yerlestirme, transfer, cikis), planner (yalnizca goruntuleme), uretim (yetkisiz).
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
DEPO = "depo@olivamore.com"
PLANNER = "planner@olivamore.com"
S = "/api/finished-product-storages"

# ---- okuma ---------------------------------------------------------------
c("liste", "GET", S)
for n in [1, 2, 3]:
    c(f"detay {n}", "GET", f"{S}/{n}")
c("olmayan", "GET", S + "/999999")
c("hareketler 2", "GET", S + "/2/movements")
c("hareketler 3", "GET", S + "/3/movements")
c("hareketler olmayan", "GET", S + "/999999/movements")
c("liste planlamaci", "GET", S, as_user=PLANNER)
c("liste yetkisiz", "GET", S, as_user="uretim@olivamore.com")

# ---- depo ------------------------------------------------------------------
c("depo ekle bos", "POST", S, {})
c("depo ekle hatali", "POST", S, {"name": "x" * 256, "description": 5, "is_active": None})
c("depo ekle", "POST", S, {"name": "Test Depo", "description": "deneme"}, ("ST1", "data.id"))
c("depo ekle pasif", "POST", S, {"name": "Pasif Depo", "is_active": "0"}, ("ST2", "data.id"))
c("depo ekle planlamaci", "POST", S, {"name": "x"}, as_user=PLANNER)
c("depo guncelle", "PATCH", S + "/{ST1}", {"name": "Test Depo 1", "description": None})
c("depo guncelle bos", "PUT", S + "/{ST2}", {})
c("depo guncelle hatali", "PATCH", S + "/{ST2}", {"name": None, "is_active": "evet"})

# ---- raf -------------------------------------------------------------------
c("raf ekle bos", "POST", S + "/{ST1}/slots", {})
c("raf ekle hatali", "POST", S + "/{ST1}/slots", {"name": "x" * 51, "volume_litre": 0, "max_quantity": 0,
                                                  "finished_product_id": 999})
c("raf ekle hacim uymuyor", "POST", S + "/{ST1}/slots", {"volume_litre": 0.5, "max_quantity": 10, "finished_product_id": 1})
c("raf ekle", "POST", S + "/{ST1}/slots", {"name": "T1", "volume_litre": 0.25, "max_quantity": 100}, ("SL1", "data.id"))
c("raf ekle kilitli", "POST", S + "/{ST1}/slots", {"name": "T2", "volume_litre": "0.2500", "max_quantity": "50",
                                                   "finished_product_id": 1}, ("SL2", "data.id"))
c("raf ekle isimsiz", "POST", S + "/{ST1}/slots", {"volume_litre": "0.33333", "max_quantity": 40}, ("SL3", "data.id"))
c("raf ekle pasif depo", "POST", S + "/{ST2}/slots", {"name": "P1", "volume_litre": 0.25, "max_quantity": 10}, ("SL4", "data.id"))
c("raf guncelle hacim ve kilit", "PATCH", S + "/{ST1}/slots/{SL3}", {"volume_litre": 0.5, "finished_product_id": 19})
c("raf guncelle kilit hacim uymuyor", "PATCH", S + "/{ST1}/slots/{SL3}", {"finished_product_id": 1})
c("raf guncelle baska depo url", "PATCH", S + "/1/slots/{SL3}", {"name": "T3"})
c("raf guncelle hatali", "PATCH", S + "/{ST1}/slots/{SL3}", {"volume_litre": "abc", "max_quantity": 0, "finished_product_id": 999})
c("raf guncelle olmayan", "PATCH", S + "/{ST1}/slots/999999", {"name": "x"})
c("raf detay", "GET", S + "/{ST1}")

# ---- depoya yerlestirme --------------------------------------------------------
cap.sql("acikta sise", "update finished_products set total_stock = total_stock + 100 where id = 1 returning id")
cap.sql("acikta sise 18", "update finished_products set total_stock = total_stock + 60 where id = 18 returning id")
cap.sql("acikta sise 19", "update finished_products set total_stock = total_stock + 30 where id = 19 returning id")
IN = S + "/{ST1}/stock-in"
c("giris bos", "POST", IN, {})
c("giris raf listesi bos", "POST", IN, {"finished_product_id": 1, "placements": []})
c("giris raf hatali", "POST", IN, {"finished_product_id": 1, "placements": [{"finished_product_storage_slot_id": 999999, "quantity": 0}, {}],
                                   "note": "x" * 1001, "finished_product_run_id": 999999})
c("giris tek raf", "POST", IN, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 40,
                                "note": "dolumdan", "finished_product_run_id": 1})
c("giris coklu raf", "POST", IN, {"finished_product_id": 1, "placements": [
    {"finished_product_storage_slot_id": "{SL1}", "quantity": 20}, {"finished_product_storage_slot_id": "{SL2}", "quantity": "30"}]})
c("giris ayni raf iki kez", "POST", IN, {"finished_product_id": 1, "placements": [
    {"finished_product_storage_slot_id": "{SL1}", "quantity": 1}, {"finished_product_storage_slot_id": "{SL1}", "quantity": 1}]})
c("giris acikta yetersiz", "POST", IN, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 50})
c("giris baska deponun rafi", "POST", IN, {"finished_product_id": 1, "finished_product_storage_slot_id": 1, "quantity": 1})
c("giris hacim uymuyor", "POST", IN, {"finished_product_id": 19, "finished_product_storage_slot_id": "{SL1}", "quantity": 1})
c("giris kilitli raf", "POST", IN, {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL2}", "quantity": 1})
c("giris kapasite", "POST", IN, {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL1}", "quantity": 50})
c("giris pasif depo", "POST", S + "/{ST2}/stock-in", {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL4}",
                                                      "quantity": 1})
c("giris depo", "POST", IN, {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL1}", "quantity": 25}, as_user=DEPO)
c("giris kilitli hacimli", "POST", IN, {"finished_product_id": 19, "finished_product_storage_slot_id": "{SL3}", "quantity": 30})
c("giris planlamaci", "POST", IN, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 1},
  as_user=PLANNER)
c("urun 1 yerlestirmeden sonra", "GET", "/api/finished-products/1")

# ---- raf kurallari (dolu raf) -------------------------------------------------------
R1 = S + "/{ST1}/slots/{SL1}"
c("raf kapasite kullanimin altinda", "PATCH", R1, {"max_quantity": "10"})
c("raf dolu hacim degismez", "PATCH", R1, {"volume_litre": 0.5})
c("raf dolu ayni hacim", "PATCH", R1, {"volume_litre": "0.25", "max_quantity": 200})
c("raf baska urun varken kilit", "PATCH", R1, {"finished_product_id": 18})
c("raf kilit kaldir", "PATCH", S + "/{ST1}/slots/{SL2}", {"finished_product_id": None, "name": None})
c("raf sil dolu", "DELETE", R1)

# ---- transfer ---------------------------------------------------------------------
T = S + "/{ST1}/transfer"
c("transfer bos", "POST", T, {})
c("transfer ayni raf", "POST", T, {"finished_product_id": 1, "source_slot_id": "{SL1}", "target_slot_id": "{SL1}", "quantity": 1})
c("transfer rafta yok", "POST", T, {"finished_product_id": 19, "source_slot_id": "{SL1}", "target_slot_id": "{SL3}", "quantity": 1})
c("transfer rafta yetersiz", "POST", T, {"finished_product_id": 1, "source_slot_id": "{SL1}", "target_slot_id": 1, "quantity": 500})
c("transfer pasif depoya", "POST", T, {"finished_product_id": 1, "source_slot_id": "{SL1}", "target_slot_id": "{SL4}", "quantity": 1})
c("transfer kaynak baska depoda", "POST", S + "/2/transfer", {"finished_product_id": 1, "source_slot_id": "{SL1}",
                                                              "target_slot_id": 1, "quantity": 1})
c("transfer baska depoya", "POST", T, {"finished_product_id": 1, "source_slot_id": "{SL1}", "target_slot_id": 1, "quantity": 10})
c("transfer ayni depo", "POST", T, {"finished_product_id": 18, "source_slot_id": "{SL1}", "target_slot_id": "{SL2}", "quantity": 20},
  as_user=DEPO)

# ---- cikis -------------------------------------------------------------------------
O = S + "/{ST1}/stock-out"
c("cikis bos", "POST", O, {})
c("cikis", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 5})
c("cikis yetersiz", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 1000})
c("cikis baska deponun rafi", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": 14, "quantity": 1})
c("cikis rafi bosalt", "POST", O, {"finished_product_id": 19, "finished_product_storage_slot_id": "{SL3}", "quantity": 30}, as_user=DEPO)
c("cikis planlamaci", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 1},
  as_user=PLANNER)
c("hareketler test deposu", "GET", S + "/{ST1}/movements")
c("hareketler ana depo", "GET", S + "/1/movements")
c("urun 1 sonra", "GET", "/api/finished-products/1")
c("detay test deposu", "GET", S + "/{ST1}")
c("liste sonra", "GET", S)

# ---- silme ---------------------------------------------------------------------------
c("raf sil", "DELETE", S + "/{ST2}/slots/{SL4}")
c("raf sil olmayan", "DELETE", S + "/{ST2}/slots/{SL4}")
c("depo sil stoklu", "DELETE", S + "/1")
c("depo sil", "DELETE", S + "/{ST2}")
c("depo sil olmayan", "DELETE", S + "/{ST2}")

# ---- Laravel 500'leri (Java'da 422, DEVIATIONS 15) -------------------------------------
c("raf sil hareketli", "DELETE", S + "/{ST1}/slots/{SL3}")
c("depo bosalt 1", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL1}", "quantity": 45})
c("depo bosalt 2", "POST", O, {"finished_product_id": 1, "finished_product_storage_slot_id": "{SL2}", "quantity": 30})
c("depo bosalt 3", "POST", O, {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL2}", "quantity": 20})
c("depo bosalt 4", "POST", O, {"finished_product_id": 18, "finished_product_storage_slot_id": "{SL1}", "quantity": 5})
c("depo sil hareketli", "DELETE", S + "/{ST1}")

cap.write(HERE.parent / "src/test/resources/laravel/finished-product-storage.json")
