"""Faz 7 (ambalaj) icin Laravel'in davranisini yakalar: urun tedarikcileri, ambalaj kalemleri, lotlar ve
lot hareketleri (mal girisi, kunye duzeltmesi, sayim duzeltmesi, fire, iade, silme).

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/packaging.json (Java'da PackagingParityIT).

  python -I reference/capture_packaging.py <laravel_compose_dir> [base_url]

Kullanicilar: admin (her yetki), operator1 (mal girisi; duzeltme ve kalem yonetimi yok),
planner (tedarikci yonetimi; kalem ve lotlari yalnizca goruntuleme), depo (mal girisi).
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
OPERATOR = "operator1@olivamore.com"
PLANNER = "planner@olivamore.com"
DEPO = "depo@olivamore.com"
S = "/api/product-suppliers"
I = "/api/packaging-items"
L = "/api/packaging-lots"

# ---- urun tedarikcileri ------------------------------------------------
for q in ["", "?search=cam", "?search=TED-KAP", "?search=%20etiket%20", "?is_active=1", "?is_active=0", "?is_active="]:
    c("tedarikci liste " + (q or "varsayilan"), "GET", S + q)
for n in [1, 2, 3]:
    c(f"tedarikci detay {n}", "GET", f"{S}/{n}")
c("tedarikci olmayan", "GET", S + "/999999")
c("tedarikci yetkisiz", "GET", S, as_user="muhendis@olivamore.com")
c("tedarikci ekle bos", "POST", S, {})
c("tedarikci ekle hatali", "POST", S, {"name": 5, "code": "TED-CAM-01", "email": "x", "phone": "1" * 60, "is_active": "evet"})
c("tedarikci ekle", "POST", S, {"name": "Yeni Kutu Ltd", "code": "TED-KUT-01"}, ("S1", "data.id"))
c("tedarikci ekle tam", "POST", S, {"name": "Akdeniz Etiket", "code": "TED-ETK-02", "phone": "0232 000 00 00",
                                    "email": "info@akdeniz.test", "address": "Izmir", "note": "yedek", "is_active": "0"},
  ("S2", "data.id"))
c("tedarikci ekle null aktif", "POST", S, {"name": "Null Aktif", "code": "TED-NUL-01", "is_active": None})
c("tedarikci ekle planlamaci", "POST", S, {"name": "Planlamaci Firma", "code": "TED-PLN-01"}, ("S3", "data.id"),
  as_user=PLANNER)
c("tedarikci ekle operator", "POST", S, {"name": "Operator Firma", "code": "TED-OPR-01"}, as_user=OPERATOR)
c("tedarikci guncelle", "PATCH", S + "/{S1}", {"name": "Yeni Kutu Ltd Sti", "is_active": False, "note": "guncel"})
c("tedarikci guncelle ayni kod", "PATCH", S + "/{S1}", {"code": "TED-KUT-01"})
c("tedarikci guncelle kod cakisma", "PATCH", S + "/{S1}", {"code": "TED-CAM-01", "name": ""})
c("tedarikci guncelle degisiklik yok", "PATCH", S + "/{S2}", {})
c("tedarikci sil lotlu", "DELETE", S + "/1")
c("tedarikci sil", "DELETE", S + "/{S3}")
c("tedarikci sil olmayan", "DELETE", S + "/999999")

# ---- ambalaj kalemleri -------------------------------------------------
for q in ["", "?is_active=1", "?is_active=0", "?is_active=yes"]:
    c("kalem liste " + (q or "varsayilan"), "GET", I + q)
for n in [1, 9, 17]:
    c(f"kalem detay {n}", "GET", f"{I}/{n}")
c("kalem olmayan", "GET", I + "/999999")
c("kalem liste planlamaci", "GET", I, as_user=PLANNER)
c("kalem ekle bos", "POST", I, {})
c("kalem ekle hatali", "POST", I, {"name": "x" * 300, "type": "BOX", "current_stock": -1, "is_active": "x"})
c("kalem ekle", "POST", I, {"name": "Cam Şişe 100ml", "type": "BOTTLE"}, ("I1", "data.id"))
c("kalem ekle devir", "POST", I, {"name": "Mantar Tapa", "type": "CAP", "current_stock": 500, "is_active": None},
  ("I2", "data.id"))
c("kalem ekle devir sifir", "POST", I, {"name": "Boyun Etiketi", "type": "LABEL", "current_stock": "0", "is_active": False},
  ("I3", "data.id"))
c("kalem ekle operator", "POST", I, {"name": "Operator Kalemi", "type": "CAP"}, as_user=OPERATOR)
c("kalem devir lotu", "GET", I + "/{I2}/lots")
c("kalem guncelle", "PATCH", I + "/{I1}", {"name": "Cam Şişe 100 ml", "current_stock": 999, "is_active": "0"})
c("kalem guncelle put", "PUT", I + "/{I1}", {"type": "LABEL"})
c("kalem guncelle hatali", "PATCH", I + "/{I1}", {"type": "", "current_stock": "abc", "is_active": None})
c("kalem guncelle planlamaci", "PATCH", I + "/{I1}", {"name": "x"}, as_user=PLANNER)
c("kalem sil bilesenli", "DELETE", I + "/1")
c("kalem sil lotlu", "DELETE", I + "/{I2}")
c("kalem sil", "DELETE", I + "/{I3}")
c("kalem sil olmayan", "DELETE", I + "/999999")

# ---- lot okuma -----------------------------------------------------------
cap.sql("duzeltmeli lot", "select packaging_lot_id as id from packaging_lot_movements where source_type = 'MANUAL_ADJUSTMENT'"
        " order by id limit 1", save="ADJ")
for q in ["", "?packaging_item_id=1", "?product_supplier_id=2", "?status=active", "?status=depleted", "?status=0",
          "?search=ECA-26-04", "?search=kapak", "?search=TED-ETK", "?search=ege", "?packaging_item_id=abc",
          "?packaging_item_id=6&status=active&search=0428", "?product_supplier_id=999"]:
    c("lot liste " + (q or "varsayilan"), "GET", L + q)
c("kalem lotlari", "GET", I + "/6/lots")
c("kalem lotlari olmayan", "GET", I + "/999999/lots")
# 3: kosulara tahsisli, kismen kullanilmis; 45: cok kosulu; 1: tukenmis; 16: hic kullanilmamis
for n in [3, 45, 1, 16]:
    c(f"lot detay {n}", "GET", f"{L}/{n}")
c("lot detay duzeltmeli", "GET", L + "/{ADJ}")
c("lot olmayan", "GET", L + "/999999")
c("lot yetkisiz", "GET", L, as_user="muhendis@olivamore.com")

# ---- mal girisi --------------------------------------------------------
c("giris bos", "POST", I + "/6/lots", {})
c("giris hatali", "POST", I + "/6/lots", {"lot_code": "x" * 101, "quantity": 0, "received_at": "dun",
                                          "product_supplier_id": 999, "note": 5})
c("giris mukerrer lot", "POST", I + "/6/lots", {"lot_code": "ECA-26-0427", "quantity": 10})
c("giris", "POST", I + "/6/lots", {"lot_code": "T-LOT-1", "quantity": 100, "received_at": "2026-11-01 09:30",
                                   "product_supplier_id": 1, "note": "test girisi"}, ("L1", "data.id"))
c("giris baska kalemde ayni lot", "POST", I + "/7/lots", {"lot_code": "T-LOT-1", "quantity": "40",
                                                          "received_at": "2026-11-02T08:00:00+03:00"}, ("L2", "data.id"))
c("giris depo", "POST", I + "/7/lots", {"lot_code": "T-LOT-2", "quantity": 15, "received_at": "2026-11-03",
                                        "product_supplier_id": "{S1}"}, ("L3", "data.id"), as_user=DEPO)
c("giris planlamaci", "POST", I + "/7/lots", {"lot_code": "T-LOT-3", "quantity": 1}, as_user=PLANNER)
c("giris olmayan kalem", "POST", I + "/999999/lots", {"lot_code": "T-LOT-4", "quantity": 1})
c("kalem 6 giristen sonra", "GET", I + "/6")

# ---- kunye duzeltmesi ----------------------------------------------------
c("lot guncelle", "PATCH", L + "/{L1}", {"lot_code": "T-LOT-1A", "received_at": "2026-11-02", "product_supplier_id": None,
                                         "note": "duzeltildi"})
c("lot guncelle mukerrer", "PATCH", L + "/{L1}", {"lot_code": "ECA-26-0428"})
c("lot guncelle ayni kod", "PATCH", L + "/{L1}", {"lot_code": "T-LOT-1A", "received_at": "2026-11-02 00:00:00"})
c("lot guncelle hatali", "PATCH", L + "/{L1}", {"received_at": None, "product_supplier_id": "abc", "quantity_remaining": 1})
c("lot guncelle operator", "PATCH", L + "/{L2}", {"note": "operator"}, as_user=OPERATOR)

# ---- sayim duzeltmesi ----------------------------------------------------
c("duzeltme bos", "POST", L + "/{L1}/adjustment", {})
c("duzeltme sifir", "POST", L + "/{L1}/adjustment", {"delta": 0})
c("duzeltme sifir metin", "POST", L + "/{L1}/adjustment", {"delta": "0", "reason": "COUNT"})
c("duzeltme hatali", "POST", L + "/{L1}/adjustment", {"delta": "abc", "reason": "LOST", "note": 3})
c("duzeltme null sebep", "POST", L + "/{L1}/adjustment", {"delta": 1, "reason": None})
c("duzeltme fazla dusus", "POST", L + "/{L1}/adjustment", {"delta": -1000})
c("duzeltme fire artis", "POST", L + "/{L1}/adjustment", {"delta": 5, "reason": "SCRAP"})
c("duzeltme iade artis", "POST", L + "/{L1}/adjustment", {"delta": 5, "reason": "RETURN"})
c("duzeltme sayim fazlasi", "POST", L + "/{L1}/adjustment", {"delta": 5, "note": "sayim"})
c("duzeltme fire", "POST", L + "/{L1}/adjustment", {"delta": "-3", "reason": "SCRAP", "note": "kirik"})
c("duzeltme iade", "POST", L + "/{L1}/adjustment", {"delta": -2, "reason": "RETURN"})
c("duzeltme sayim eksigi", "POST", L + "/{L1}/adjustment", {"delta": -100, "reason": "COUNT"})
c("duzeltme tukenmis artis", "POST", L + "/{L1}/adjustment", {"delta": 1})
c("duzeltme operator", "POST", L + "/{L1}/adjustment", {"delta": 1}, as_user=OPERATOR)
c("duzeltme olmayan", "POST", L + "/999999/adjustment", {"delta": 1})
c("kalem 6 duzeltmeden sonra", "GET", I + "/6")
c("lot liste kalem 6", "GET", L + "?packaging_item_id=6")

# ---- silme ---------------------------------------------------------------
c("lot sil kullanilmis", "DELETE", L + "/3")
c("lot sil duzeltilmis", "DELETE", L + "/{L1}")
c("lot sil", "DELETE", L + "/{L2}")
c("lot sil olmayan", "DELETE", L + "/{L2}")
c("kalem 7 silmeden sonra", "GET", I + "/7")
c("tedarikci lotlu detay", "GET", S + "/{S1}")
c("tedarikci sil lotu olan yeni", "DELETE", S + "/{S1}")
c("lot sil depo", "DELETE", L + "/{L3}", as_user=DEPO)
c("tedarikci sil artik bos", "DELETE", S + "/{S1}")

cap.write(HERE.parent / "src/test/resources/laravel/packaging.json")
