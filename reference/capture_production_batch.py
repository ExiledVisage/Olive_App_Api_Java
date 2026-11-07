"""Faz 6a (uretim partileri) icin Laravel'in davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/production-batch.json (Java'da ProductionBatchParityIT).

  python -I reference/capture_production_batch.py <laravel_compose_dir> [base_url]

Seed'den: hat 1 (girdi: zeytin kategorisi, cesit serbest), hat 2 (yalnizca Gemlik zeytini), hat 3 (Ayvalik).
Tedarikci 1'in tarlalari 1, 2; tedarikci 2'nin 3. Parti 273 baslamis (silinemez).
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
B = "/api/production-batches"

# ---- liste ve takvim ---------------------------------------------------
c("liste tarih", "GET", B + "?received_from=2026-11-04&received_to=2026-11-05")
c("liste sayfa", "GET", B + "?per_page=2&page=3&received_from=2026-10-01")
c("liste tarih hatali", "GET", B + "?received_from=2026-11-4&received_to=05.11.2026")
c("takvim", "GET", B + "/calendar?from=2026-10-01&to=2026-11-30")
c("takvim bos", "GET", B + "/calendar?from=2030-01-01&to=2030-01-31")
c("takvim eksik", "GET", B + "/calendar")
c("takvim ters", "GET", B + "/calendar?from=2026-11-05&to=2026-11-01")
c("takvim hatali", "GET", B + "/calendar?from=2026-02-30&to=2026-03-01")
c("parti goster", "GET", B + "/273")
c("parti olmayan", "GET", B + "/999999")

# ---- ekleme ------------------------------------------------------------
c("parti bos", "POST", B, {})
c("parti hatali", "POST", B, {"batch_code": "P-261105-02", "input_production_product_type_ids": [1, 1, 999, "x"],
                              "production_line_id": 99, "status": "x", "quantity_kg": -1, "received_at": "dun",
                              "supplier_id": 999})
c("parti bos dizi", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [], "production_line_id": 1})
c("parti hat kategorisi", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [3], "production_line_id": 1})
c("parti hat cesidi", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [2], "production_line_id": 2})
c("parti farkli cesit", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [1, 2], "production_line_id": 1})
c("parti tarla tedarikcisiz", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [1],
                                          "production_line_id": 1, "supplier_field_id": 1})
c("parti tarla baskasinin", "POST", B, {"batch_code": "T-1", "input_production_product_type_ids": [1],
                                        "production_line_id": 1, "supplier_id": 1, "supplier_field_id": 3})
c("parti ekle", "POST", B, {"batch_code": "  T-TEST-1 ", "input_production_product_type_ids": ["1"], "production_line_id": 1,
                            "supplier_id": "1", "supplier_field_id": 1, "quantity_kg": 1234.56789, "status": "in_progress",
                            "received_at": "2026-11-06T10:15:30+03:00", "started_at": "2026-11-06 11:00", "note": "Deneme"},
  ("B", "data.id"))
c("parti ekle varsayilan", "POST", B, {"batch_code": "T-TEST-2", "input_production_product_type_ids": [2],
                                       "production_line_id": 3}, ("B2", "data.id"))
c("parti tekrar kod", "POST", B, {"batch_code": "T-TEST-1", "input_production_product_type_ids": [1], "production_line_id": 1})
c("parti goster yeni", "GET", B + "/{B}")

# ---- guncelleme --------------------------------------------------------
c("guncelle durum", "PATCH", B + "/{B}", {"status": "completed", "received_at": "2026-11-07", "completed_at": None,
                                         "quantity_kg": "10"})
c("guncelle tedarikci tarlasiz", "PATCH", B + "/{B}", {"supplier_id": 2})
c("guncelle tarla", "PATCH", B + "/{B}", {"supplier_field_id": 3})
c("guncelle tarla baskasinin", "PATCH", B + "/{B}", {"supplier_field_id": 1})
c("guncelle tedarikci kaldir", "PATCH", B + "/{B}", {"supplier_id": None})
c("guncelle tekrar kod", "PATCH", B + "/{B}", {"batch_code": "P-261105-02"})
c("guncelle kendi kodu", "PATCH", B + "/{B}", {"batch_code": "T-TEST-1", "note": None})
c("guncelle hatali", "PATCH", B + "/{B}", {"status": None, "received_at": "2026-13-01", "quantity_kg": "abc"})
c("guncelle bos", "PATCH", B + "/{B}", {})
c("takvim yeni", "GET", B + "/calendar?from=2026-11-06&to=2026-11-07")

# ---- silme -------------------------------------------------------------
c("baslamis parti sil", "DELETE", B + "/273")
c("parti sil", "DELETE", B + "/{B}")
c("parti sil 2", "DELETE", B + "/{B2}")
c("silinen parti", "GET", B + "/{B}")

cap.write(HERE.parent / "src/test/resources/laravel/production-batch.json")
