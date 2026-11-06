"""Faz 5a (tanklar) icin Laravel'in yazma davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/storage-unit.json (Java'da StorageUnitParityIT).

  python -I reference/capture_inventory.py <laravel_compose_dir> [base_url]
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

# ---- tank ekleme -------------------------------------------------------
c("tank bos", "POST", "/api/storage-units", {})
c("tank hatali", "POST", "/api/storage-units", {"name": 5, "code": "FILL-GEM-03", "capacity": 0, "capacity_unit": "TON",
                                               "is_blend_tank": "evet", "description": 3, "is_active": 2})
c("tank kapasite metin", "POST", "/api/storage-units", {"name": "X", "code": "X-1", "capacity": "abc", "capacity_unit": "KG"})
c("tank kapasite negatif", "POST", "/api/storage-units", {"name": "X", "code": "X-1", "capacity": -5, "capacity_unit": "KG"})
c("tank ekle", "POST", "/api/storage-units",
  {"name": "  Test Tank  ", "code": "TEST-TANK-1", "capacity": 1000.555, "capacity_unit": "KG", "description": ""}, ("T", "data.id"))
c("tank ekle bayraklar", "POST", "/api/storage-units",
  {"name": "Blend", "code": "TEST-TANK-2", "capacity": "2500", "capacity_unit": "LITRE", "is_blend_tank": True,
   "allow_multiple_production_product_types": "1", "allow_multiple_production_products": None, "is_active": False},
  ("T2", "data.id"))
c("tank goster", "GET", "/api/storage-units/{T}")
c("tank olmayan", "GET", "/api/storage-units/999999")

# ---- guncelleme --------------------------------------------------------
c("tank guncelle", "PATCH", "/api/storage-units/{T}", {"name": "Test Tank 2", "capacity": 1500, "is_active": "0"})
c("tank guncelle bos", "PATCH", "/api/storage-units/{T}", {})
c("tank guncelle tekrar kod", "PATCH", "/api/storage-units/{T}", {"code": "FILL-GEM-03"})
c("tank guncelle kendi kodu", "PUT", "/api/storage-units/{T}", {"code": "TEST-TANK-1", "description": "Aciklama"})
c("tank guncelle hatali", "PATCH", "/api/storage-units/{T}", {"capacity": 0, "capacity_unit": "TON", "is_active": None})
c("tank guncelle birim", "PATCH", "/api/storage-units/{T}", {"capacity_unit": "LITRE", "is_blend_tank": True})

# ---- izinli urunler / tipler ---------------------------------------------
c("izinli urun bos", "PUT", "/api/storage-units/{T}/allowed-production-products", {"production_product_ids": []})
c("izinli urun hatali", "PUT", "/api/storage-units/{T}/allowed-production-products", {"production_product_ids": [1, "x", 999]})
c("izinli urun ekle", "PUT", "/api/storage-units/{T}/allowed-production-products", {"production_product_ids": [2, "1", 2]})
c("izinli urun degistir", "PUT", "/api/storage-units/{T}/allowed-production-products", {"production_product_ids": [4, 2]})
c("izinli tip hatali", "PUT", "/api/storage-units/{T}/allowed-production-product-types", {"production_product_type_ids": "3"})
c("izinli tip ekle", "PUT", "/api/storage-units/{T}/allowed-production-product-types", {"production_product_type_ids": [3, 4]})
c("izinli tip degistir", "PUT", "/api/storage-units/{T}/allowed-production-product-types", {"production_product_type_ids": [4]})

# ---- attribute kurallari -------------------------------------------------
c("kural bos", "PUT", "/api/storage-units/{T}/attribute-rules", {"rules": []})
c("kural hatali", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"min_value_decimal": "a"}, {"production_product_attribute_definition_id": 999, "apply_mode": "x", "is_active": "evet"}]})
c("kural decimal degil", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": 4}, {"production_product_attribute_definition_id": 5}]})
c("kural min max", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": 4, "min_value_decimal": 2, "max_value_decimal": "1.5"}]})
c("kural ekle", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": 4, "max_value_decimal": 0.8, "note": "Asit"},
             {"production_product_attribute_definition_id": "2", "min_value_decimal": "0.12345", "apply_mode": "incoming_only",
              "is_active": False}]})
c("kural guncelle", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": 2, "min_value_decimal": 0.1235, "apply_mode": "tank_average_only"},
             {"production_product_attribute_definition_id": 3, "min_value_decimal": 10, "max_value_decimal": 30}]})
c("kural sonradan hata", "PUT", "/api/storage-units/{T}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": 8, "max_value_decimal": 1},
             {"production_product_attribute_definition_id": 1}]})
c("tank kurallarla", "GET", "/api/storage-units/{T}")

# ---- bakiyeler / hareketler ---------------------------------------------
c("bakiyeler", "GET", "/api/storage-units/15/balances")
c("bakiyeler bos", "GET", "/api/storage-units/{T}/balances")
c("hareketler bos", "GET", "/api/storage-units/{T}/movements")
c("hareketler sayfa", "GET", "/api/storage-units/15/movements?per_page=5&page=2")
c("hareketler sayfa disi", "GET", "/api/storage-units/1/movements?per_page=5&page=9")
c("hareketler per_page hatali", "GET", "/api/storage-units/1/movements?per_page=0")
c("hareketler olmayan tank", "GET", "/api/storage-units/999999/movements")

# ---- silme -------------------------------------------------------------
c("materyalli tanki sil", "DELETE", "/api/storage-units/15")
c("hareketli tanki sil", "DELETE", "/api/storage-units/13")
c("tank sil", "DELETE", "/api/storage-units/{T}")
c("tank sil 2", "DELETE", "/api/storage-units/{T2}")
c("silinen tank", "GET", "/api/storage-units/{T2}")

cap.write(HERE.parent / "src/test/resources/laravel/storage-unit.json")
