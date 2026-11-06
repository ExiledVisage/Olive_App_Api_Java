"""Faz 5c (stok hareketleri) icin Laravel'in yazma davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/storage-movement.json (Java'da StorageMovementParityIT).

  python -I reference/capture_movement.py <laravel_compose_dir> [base_url]

Seed'den: tank 1 (FILL-GEM-03) yalnizca tip 3, asitlik 0.2..0.4 (incoming_only), 2950/3000 L dolu;
tank 13 (DOLUM-AYV-01) bos, yalnizca tip 4; tank 7'de 2349 (340 L) ve 2354 (908 L); tank 15 tip 3, 3990/6000 L.
Materyal 11 ve 13 pirina (tip 5, KG), tankta degil. 3276: P-261105-02 partisinin prosesi (ciktilari 4, 5).
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
ACID = 4  # zeytinyagi asitlik tanimi (decimal)


def acid(value):
    return [{"production_product_attribute_definition_id": ACID, "value_decimal": value}]


# ---- acilis: validation ve tank kurallari --------------------------------
c("acilis bos", "POST", "/api/storage-movements/opening", {})
c("acilis hatali", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 999, "production_product_type_id": "x", "quantity": 0, "unit": "TON",
   "attribute_values": [{"value_decimal": "abc"}, {"production_product_attribute_definition_id": 999, "value_json": "x"}]})
c("acilis birim uyusmuyor", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 10, "unit": "KG"})
c("acilis izinsiz urun", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 6, "quantity": 10, "unit": "LITRE"})
c("acilis izinsiz tip", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 4, "quantity": 10, "unit": "LITRE"})
c("acilis kapasite", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 50.01, "unit": "LITRE", "attribute_values": acid(0.3)})
c("acilis attribute yok", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 10, "unit": "LITRE"})
c("acilis asitlik yuksek", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 10, "unit": "LITRE", "attribute_values": acid("0.41")})
c("acilis gecersiz tanim", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 10, "unit": "LITRE",
   "attribute_values": [{"production_product_attribute_definition_id": 2, "value_decimal": 1}]})
c("acilis", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": 1, "production_product_type_id": 3, "quantity": 12.34567, "unit": "LITRE", "note": "Devir",
   "attribute_values": [{"production_product_attribute_definition_id": ACID, "value_decimal": 0.25, "value_text": "x",
                         "value_json": {"a": 1}}]}, ("M1", "data.material.id"))
c("tank 1 bakiyeler", "GET", "/api/storage-units/1/balances")

# ---- ortalama kurali (yeni tank) -------------------------------------------
c("ortalama tanki", "POST", "/api/storage-units",
  {"name": "Ortalama", "code": "TEST-AVG", "capacity": 5000, "capacity_unit": "LITRE"}, ("AVG", "data.id"))
c("ortalama kurali", "PUT", "/api/storage-units/{AVG}/attribute-rules",
  {"rules": [{"production_product_attribute_definition_id": ACID, "min_value_decimal": 0.2, "max_value_decimal": 0.5,
              "apply_mode": "tank_average_only"}]})
c("ortalama ilk asiyor", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 3, "quantity": 1000, "unit": "LITRE", "attribute_values": acid(0.6)})
c("ortalama ilk", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 3, "quantity": 1000, "unit": "LITRE", "attribute_values": acid(0.3)})
c("ortalama ikinci", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 3, "quantity": 500, "unit": "LITRE", "attribute_values": acid(0.8)})
c("ortalama ucuncu asiyor", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 3, "quantity": 1000, "unit": "LITRE", "attribute_values": acid(0.9)})
c("farkli tip", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 4, "quantity": 10, "unit": "LITRE", "attribute_values": acid(0.3)})
c("farkli urun", "POST", "/api/storage-movements/opening",
  {"storage_unit_id": "{AVG}", "production_product_type_id": 6, "quantity": 10, "unit": "LITRE"})
c("ortalama tanki goster", "GET", "/api/storage-units/{AVG}")

# ---- giris: var olan materyal ve yeni materyal -----------------------------
c("giris bos", "POST", "/api/storage-movements/inbound", {"storage_unit_id": 13, "quantity": 5})
c("giris ikisi birden", "POST", "/api/storage-movements/inbound",
  {"storage_unit_id": 13, "quantity": 5, "material_id": 11, "production_product_type_id": 4, "unit": "LITRE"})
c("pirina tanki", "POST", "/api/storage-units",
  {"name": "Pirina", "code": "TEST-KG", "capacity": 10000, "capacity_unit": "KG"}, ("KG", "data.id"))
c("giris parca", "POST", "/api/storage-movements/inbound",
  {"storage_unit_id": "{KG}", "material_id": 11, "quantity": "600.5", "note": "Kismi"}, ("P1", "data.material.id"))
c("giris tamami", "POST", "/api/storage-movements/inbound", {"storage_unit_id": "{KG}", "material_id": 13, "quantity": 20})
c("giris fazla", "POST", "/api/storage-movements/inbound", {"storage_unit_id": "{KG}", "material_id": 11, "quantity": 99999})
c("giris kapasite birimi", "POST", "/api/storage-movements/inbound", {"storage_unit_id": 13, "material_id": 11, "quantity": 1})
c("giris parti ciktisi degil", "POST", "/api/storage-movements/inbound",
  {"storage_unit_id": 13, "production_product_type_id": 3, "quantity": 100, "batch_process_id": 3276})
c("giris partiden", "POST", "/api/storage-movements/inbound",
  {"storage_unit_id": 13, "production_product_type_id": 4, "quantity": 100, "batch_process_id": 3276,
   "attribute_values": acid(0.4)}, ("M13", "data.material.id"))
c("giris birimsiz", "POST", "/api/storage-movements/inbound",
  {"storage_unit_id": 13, "production_product_type_id": 4, "quantity": 0.5})
c("pirina tanki goster", "GET", "/api/storage-units/{KG}")

# ---- cikis -------------------------------------------------------------
c("cikis bos", "POST", "/api/storage-movements/outbound", {})
c("cikis tankta degil", "POST", "/api/storage-movements/outbound", {"storage_unit_id": 13, "material_id": 1, "quantity": 1})
c("cikis tuketilmis", "POST", "/api/storage-movements/outbound", {"storage_unit_id": 13, "material_id": 1975, "quantity": 1})
c("cikis fazla", "POST", "/api/storage-movements/outbound", {"storage_unit_id": 13, "material_id": "{M13}", "quantity": 100.0001})
c("cikis parca", "POST", "/api/storage-movements/outbound",
  {"storage_unit_id": 13, "material_id": "{M13}", "quantity": 40, "batch_process_id": 3276, "note": "Dolum"})

# ---- transfer ----------------------------------------------------------
c("transfer ayni tank", "POST", "/api/storage-movements/transfer",
  {"from_storage_unit_id": 7, "to_storage_unit_id": 7, "material_id": 2349, "quantity": 1})
c("transfer tankta degil", "POST", "/api/storage-movements/transfer",
  {"from_storage_unit_id": 8, "to_storage_unit_id": 15, "material_id": 2349, "quantity": 1})
c("transfer kapasite", "POST", "/api/storage-movements/transfer",
  {"from_storage_unit_id": 7, "to_storage_unit_id": 1, "material_id": 2354, "quantity": 908})
c("transfer parca", "POST", "/api/storage-movements/transfer",
  {"from_storage_unit_id": 7, "to_storage_unit_id": 15, "material_id": 2349, "quantity": 100.25, "note": "Rezerv"})
c("transfer tamami", "POST", "/api/storage-movements/transfer",
  {"from_storage_unit_id": 7, "to_storage_unit_id": 15, "material_id": 2354, "quantity": 908})
c("tank 7 bakiyeler", "GET", "/api/storage-units/7/balances")
c("tank 15 bakiyeler", "GET", "/api/storage-units/15/balances")

# ---- duzeltme ----------------------------------------------------------
c("duzeltme bos", "POST", "/api/storage-movements/adjustment", {})
c("duzeltme artis eksik", "POST", "/api/storage-movements/adjustment",
  {"storage_unit_id": 13, "quantity": 5, "is_increase": True})
c("duzeltme metin", "POST", "/api/storage-movements/adjustment",
  {"storage_unit_id": 13, "quantity": 5, "is_increase": "evet"})
c("duzeltme artis", "POST", "/api/storage-movements/adjustment",
  {"storage_unit_id": 13, "quantity": 20, "is_increase": "1", "production_product_type_id": 4, "unit": "LITRE",
   "attribute_values": acid(0.45), "batch_process_id": 3276})
c("duzeltme azalis", "POST", "/api/storage-movements/adjustment",
  {"storage_unit_id": 13, "quantity": 60, "is_increase": False, "material_id": "{M13}"})
c("tank 13 bakiyeler", "GET", "/api/storage-units/13/balances")

# ---- gecmis ------------------------------------------------------------
c("gecmis", "GET", "/api/storage-units/13/movement-history")
c("gecmis tarih", "GET", "/api/storage-units/7/movement-history?date_from=2026-11-01&date_to=2026-11-05")
c("gecmis bos aralik", "GET", "/api/storage-units/7/movement-history?date_from=2030-01-01")
c("gecmis sayfa", "GET", "/api/storage-units/7/movement-history?per_page=3&page=2&date_from=0")
c("gecmis olmayan", "GET", "/api/storage-units/999999/movement-history")
c("materyal detay", "GET", "/api/materials/2349")
c("materyal soy", "GET", "/api/materials/11/lineage")

cap.write(HERE.parent / "src/test/resources/laravel/storage-movement.json")
