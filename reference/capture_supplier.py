"""Faz 4 (tedarikciler) icin Laravel'in yazma davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/supplier.json (Java'da SupplierParityIT).

  python -I reference/capture_supplier.py <laravel_compose_dir> [base_url]
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

# ---- tedarikciler ------------------------------------------------------
c("tedarikci bos", "POST", "/api/suppliers", {})
c("tedarikci tekrar kod", "POST", "/api/suppliers", {"name": "X", "code": "TED-108"})
c("tedarikci hatali", "POST", "/api/suppliers", {"name": 5, "code": "T-1", "email": "abc", "is_active": "evet",
                                                "fields": [{"code": "TR-1"}, {"code": "TR-1"}, {"code": "BRH-1250"}, {"location": "x"}]})
c("tedarikci ekle", "POST", "/api/suppliers",
  {"name": "  Test Tedarikci  ", "code": "TED-TEST", "phone": "0555", "email": "t@o.co", "note": "",
   "fields": [{"code": "TR-TEST-1", "location": "Ayvalik", "area_decare": 12.345}, {"code": "TR-TEST-2", "area_decare": "7"}]}, ("S", "data.id"))
c("tedarikci ekle aktif", "POST", "/api/suppliers", {"name": "Aktif", "code": "TED-AKTIF", "is_active": False}, ("S2", "data.id"))
c("tedarikci goster", "GET", "/api/suppliers/{S}")
c("tedarikci guncelle", "PATCH", "/api/suppliers/{S}", {"name": "Test Tedarikci 2", "is_active": "0"})
c("tedarikci guncelle tekrar kod", "PATCH", "/api/suppliers/{S}", {"code": "TED-108"})
c("tedarikci guncelle kendi kodu", "PATCH", "/api/suppliers/{S}", {"code": "TED-TEST"})
c("tedarikci put", "PUT", "/api/suppliers/{S}", {"name": "X"})
c("tedarikci arama", "GET", "/api/suppliers?search=test")
c("tedarikci pasifler", "GET", "/api/suppliers?is_active=false")
c("tedarikci teslimat yok", "GET", "/api/suppliers/{S}/deliveries")
c("kullanilan tedarikciyi sil", "DELETE", "/api/suppliers/10")

# ---- tarlalar ----------------------------------------------------------
c("tarla bos", "POST", "/api/suppliers/{S}/fields", {})
c("tarla ekle", "POST", "/api/suppliers/{S}/fields", {"code": "TR-TEST-3", "area_decare": 1.5, "is_active": True}, ("F", "data.id"))
c("tarla ekle varsayilan", "POST", "/api/suppliers/{S}/fields", {"code": "TR-TEST-4"}, ("F2", "data.id"))
c("tarla tekrar kod", "POST", "/api/suppliers/{S}/fields", {"code": "BRH-1250"})
c("tarla baska tedarikci (validation once)", "PATCH", "/api/suppliers/10/fields/{F}", {"area_decare": -1})
c("tarla baska tedarikci", "PATCH", "/api/suppliers/10/fields/{F}", {"note": "x"})
c("tarla guncelle", "PATCH", "/api/suppliers/{S}/fields/{F}", {"note": "Not", "area_decare": None, "code": "TR-TEST-3"})
c("kullanilan tarlayi sil", "DELETE", "/api/suppliers/10/fields/16")
c("baska tedarikcinin tarlasini sil", "DELETE", "/api/suppliers/{S}/fields/16")
c("tarla sil", "DELETE", "/api/suppliers/{S}/fields/{F2}")
c("tedarikci goster tarlalarla", "GET", "/api/suppliers/{S}")

# ---- temizlik ----------------------------------------------------------
c("tarlali tedarikciyi sil", "DELETE", "/api/suppliers/{S}")
c("tedarikci sil", "DELETE", "/api/suppliers/{S2}")
c("silinen tedarikci", "GET", "/api/suppliers/{S2}")

cap.write(HERE.parent / "src/test/resources/laravel/supplier.json")
