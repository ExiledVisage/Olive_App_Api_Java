"""Faz 4 (catalog) icin Laravel'in yazma davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/catalog.json (Java'da CatalogParityIT ayni sirayla oynatir).
Yollardaki {name} yer tutuculari onceki cevaplardan gelen id'lerle doldurulur; fixture'a sablon
olarak yazilir, boylece Java tarafi kendi id'leriyle ayni zinciri kurar.

  python -I reference/capture_catalog.py <laravel_compose_dir> [base_url]

"sql" adimlari: Laravel API'si ile yapilamayan hazirliklar (orn. attribute eslemesi ekleme Laravel'de
her zaman 500). Ayni SQL Java testinde de calistirilir.
"""
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

COMPOSE_DIR = sys.argv[1]
BASE = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8001"
OUT = Path(__file__).resolve().parent.parent / "src/test/resources/laravel/catalog.json"


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Accept", "application/json")
    if data is not None:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as x:
            status, text = x.status, x.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    try:
        return status, json.loads(text) if text else None
    except ValueError:
        return status, text[:200]


token = call("POST", "/api/auth/login", body={"email": "admin@olivamore.com", "password": "123"})[1]["token"]
ids = {}
cases = []


def fill(value):
    """Govdedeki "{X}" yer tutuculari kayitli id ile degistirilir."""
    if isinstance(value, str) and re.fullmatch(r"\{\w+\}", value):
        return ids[value[1:-1]]
    if isinstance(value, list):
        return [fill(v) for v in value]
    if isinstance(value, dict):
        return {k: fill(v) for k, v in value.items()}
    return value


def case(name, method, path, body=None, save=None):
    """save: cevaptan id kaydi, ornek ("P", "data.id")"""
    concrete = re.sub(r"\{(\w+)\}", lambda m: str(ids[m.group(1)]), path)
    status, parsed = call(method, concrete, token, fill(body))
    cases.append({"name": name, "method": method, "path": path, "body": body, "status": status,
                  "response": parsed, "save": list(save) if save else None})
    if save and status < 300:
        node = parsed
        for part in save[1].split("."):
            node = node[int(part)] if isinstance(node, list) else node[part]
        ids[save[0]] = node
    print(f"{status} {name}")
    return parsed


def sql_step(name, sql, save=None):
    concrete = re.sub(r"\{(\w+)\}", lambda m: str(ids[m.group(1)]), sql)
    out = subprocess.run(["docker", "compose", "exec", "-T", "db", "sh", "-c",
                          'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$0"', concrete],
                         cwd=COMPOSE_DIR, capture_output=True, text=True, check=True).stdout.strip().splitlines()
    cases.append({"name": name, "sql": sql, "save": save})
    if save:
        ids[save] = int(out[0].split("|")[0])
    print(f"SQL {name}")


# ---- urun kategorileri -------------------------------------------------
case("kategori bos", "POST", "/api/production-products", {})
case("kategori tekrar", "POST", "/api/production-products", {"key": "olive", "name": "Zeytin"})
case("kategori yogunluk hatali", "POST", "/api/production-products", {"key": "k1", "name": "K1", "default_density_kg_per_litre": 30})
case("kategori yogunluk metin", "POST", "/api/production-products", {"key": "k1", "name": "K1", "default_density_kg_per_litre": "abc"})
case("kategori ekle", "POST", "/api/production-products",
     {"key": "test_kategori", "name": "  Test Kategori  ", "description": "Deneme", "default_density_kg_per_litre": 0.92}, ("P", "data.id"))
case("kategori guncelle", "PATCH", "/api/production-products/{P}", {"name": "Test Kategori 2"})
case("kategori guncelle degisiklik yok", "PUT", "/api/production-products/{P}", {"name": "Test Kategori 2"})
case("kategori guncelle tekrar anahtar", "PUT", "/api/production-products/{P}", {"key": "olive"})
case("kategori yogunluk temizle", "PATCH", "/api/production-products/{P}", {"default_density_kg_per_litre": None})
case("kullanilan kategoriyi sil", "DELETE", "/api/production-products/1")

# ---- urun tipleri ------------------------------------------------------
case("tip bos", "POST", "/api/production-product-types", {})
case("tip ekle", "POST", "/api/production-product-types",
     {"name": "Test Tip", "code": "TEST_TIP", "type": "deneme", "unit": "KG", "production_product_id": "{P}", "density_kg_per_litre": 0.9}, ("T", "data.id"))
case("tip kucuk harf birim (veritabani kisiti)", "POST", "/api/production-product-types",
     {"name": "X", "code": "TEST_X", "type": "x", "unit": "kg", "production_product_id": 1})
case("tip tekrar kod", "POST", "/api/production-product-types",
     {"name": "X", "code": "TEST_TIP", "type": "x", "unit": "KG", "production_product_id": 1})
case("tip olmayan kategori", "PATCH", "/api/production-product-types/{T}", {"production_product_id": 999999})
case("tip yogunluk temizle", "PATCH", "/api/production-product-types/{T}", {"density_kg_per_litre": None})
case("tip goster", "GET", "/api/production-product-types/{T}")
case("kategori goster", "GET", "/api/production-products/{P}")
case("kullanilan tipi sil", "DELETE", "/api/production-product-types/1")

# ---- attribute eslemeleri ----------------------------------------------
case("esleme bos", "POST", "/api/production-products/{P}/attribute-definitions", {})
case("esleme ekle", "POST", "/api/production-products/{P}/attribute-definitions",
     {"attribute_key": "test_attr", "label": "Test", "data_type": "decimal", "unit": "%", "is_required": True, "sort_order": 5}, ("M", "data.id"))
case("esleme mevcut global anahtar", "POST", "/api/production-products/{P}/attribute-definitions",
     {"attribute_key": "acidity", "label": "Baska Etiket", "data_type": "text"}, ("M2", "data.id"))
# Laravel'de esleme eklemek 500 (unique kuralinda whereHas): guncelleme/silme icin SQL ile
sql_step("esleme hazirla", "with d as (insert into attribute_definitions (attribute_key, label, data_type, unit, created_at, updated_at) "
         "values ('test_attr', 'Test', 'decimal', '%', '2026-11-06 16:00:00', '2026-11-06 16:00:00') returning id) "
         "insert into production_product_attribute_definitions (production_product_id, attribute_definition_id, is_required, sort_order, created_at, updated_at) "
         "select {P}, d.id, true, 5, '2026-11-06 16:00:00', '2026-11-06 16:00:00' from d returning id", "M")
sql_step("esleme hazirla 2", "insert into production_product_attribute_definitions (production_product_id, attribute_definition_id, is_required, sort_order, created_at, updated_at) "
         "select {P}, id, false, 0, '2026-11-06 16:00:00', '2026-11-06 16:00:00' from attribute_definitions where attribute_key = 'acidity' returning id", "M2")
case("esleme guncelle", "PATCH", "/api/production-products/{P}/attribute-definitions/{M}",
     {"label": "Yeni Etiket", "is_required": False, "unit": None})
case("esleme guncelle anahtar", "PATCH", "/api/production-products/{P}/attribute-definitions/{M}", {"attribute_key": "baska"})
case("esleme baska kategori", "GET", "/api/production-products/1/attribute-definitions/{M}")
case("esleme listesi", "GET", "/api/production-products/{P}/attribute-definitions")
case("esleme sil", "DELETE", "/api/production-products/{P}/attribute-definitions/{M2}")
case("kullanilan eslemeyi sil", "DELETE", "/api/production-products/1/attribute-definitions/1")

# ---- proses tipleri ve alanlari ----------------------------------------
case("proses bos", "POST", "/api/process-types", {})
case("proses kategori hatali", "POST", "/api/process-types", {"name": "X", "category": "yok"})
case("proses ekle", "POST", "/api/process-types", {"name": "Test Proses", "category": "quality", "is_active": False}, ("PT", "data.id"))
case("proses tekrar ad", "PATCH", "/api/process-types/{PT}", {"name": "Kantarda Tartım"})
case("proses aktif metin", "PATCH", "/api/process-types/{PT}", {"is_active": "1", "description": "Aciklama"})
case("kullanilan prosesi sil", "DELETE", "/api/process-types/1")
case("alan bos", "POST", "/api/process-types/{PT}/fields", {})
case("alan ekle", "POST", "/api/process-types/{PT}/fields",
     {"field_key": "f1", "label": "F1", "data_type": "json", "options": {"a": [1, 2]}, "default_value": "x", "sort_order": 3}, ("F", "data.id"))
case("alan ekle 2", "POST", "/api/process-types/{PT}/fields", {"field_key": "f2", "label": "F2", "data_type": "decimal", "unit": "kg"}, ("F2", "data.id"))
case("alan tekrar anahtar", "POST", "/api/process-types/{PT}/fields", {"field_key": "f1", "label": "X", "data_type": "text"})
case("alan baska proseste ayni anahtar", "POST", "/api/process-types/1/fields", {"field_key": "f1", "label": "X", "data_type": "text"}, ("F3", "data.id"))
case("alan yanlis proses (validation once)", "PATCH", "/api/process-types/1/fields/{F}", {"data_type": "yok"})
case("alan yanlis proses", "PATCH", "/api/process-types/1/fields/{F}", {"label": "X"})
case("alan guncelle", "PATCH", "/api/process-types/{PT}/fields/{F}", {"label": "F1b", "options": None, "is_required": True})
case("alan listesi", "GET", "/api/process-types/{PT}/fields")
case("proses goster", "GET", "/api/process-types/{PT}")
case("alan sil", "DELETE", "/api/process-types/{PT}/fields/{F2}")
case("kullanilan alani sil", "DELETE", "/api/process-types/1/fields/1")
case("baska proses alanini sil", "DELETE", "/api/process-types/1/fields/{F3}")

# ---- hatlar ------------------------------------------------------------
case("hat bos", "POST", "/api/production-lines", {})
case("hat tekrar urun", "POST", "/api/production-lines",
     {"name": "X", "input_production_products": [{"production_product_id": 1}, {"production_product_id": "1"}],
      "output_production_products": [{"production_product_id": 2}]})
case("hat ekle", "POST", "/api/production-lines",
     {"name": "Test Hat", "description": "Deneme hatti",
      "input_production_products": [{"production_product_id": 1}, {"production_product_id": "{P}", "production_product_type_id": "{T}", "sort_order": 5}],
      "output_production_products": [{"production_product_id": 2}]}, ("L", "data.id"))
case("hat guncelle", "PATCH", "/api/production-lines/{L}", {"description": None, "output_production_products": [{"production_product_id": 3}]})
case("hat girdi eksik urun", "PATCH", "/api/production-lines/{L}", {"input_production_products": [{"sort_order": 1}]})
case("kullanilan hatti sil", "DELETE", "/api/production-lines/1")

# ---- asamalar (sira kaydirma) ------------------------------------------
case("asama bos", "POST", "/api/production-lines/{L}/stages", {})
case("asama A", "POST", "/api/production-lines/{L}/stages", {"name": "A", "stage_order": 1}, ("SA", "data.id"))
case("asama B basa", "POST", "/api/production-lines/{L}/stages", {"name": "B", "stage_order": 1, "process_type_id": "{PT}"}, ("SB", "data.id"))
case("asama C sona (sinir)", "POST", "/api/production-lines/{L}/stages", {"name": "C", "stage_order": 99, "requires_approval": True}, ("SC", "data.id"))
case("asama B sona tasi", "PATCH", "/api/production-lines/{L}/stages/{SB}", {"stage_order": 3})
case("asama yanlis hat", "PATCH", "/api/production-lines/1/stages/{SA}", {"name": "X"})
case("asama null zorunlu (veritabani)", "POST", "/api/production-lines/{L}/stages", {"name": "D", "stage_order": 1, "is_required": None})
case("asama listesi", "GET", "/api/production-lines/{L}/stages")
case("asama A sil", "DELETE", "/api/production-lines/{L}/stages/{SA}")
case("asama listesi sildikten sonra", "GET", "/api/production-lines/{L}/stages")

# ---- asama uzantilari --------------------------------------------------
case("uzanti bos", "PUT", "/api/production-lines/{L}/stages/{SC}/extensions", {})
case("uzanti bos dizi", "PUT", "/api/production-lines/{L}/stages/{SC}/extensions", {"extensions": []})
case("uzanti tekrar tip", "PUT", "/api/production-lines/{L}/stages/{SC}/extensions",
     {"extensions": [{"stage_extension_type_id": 1}, {"stage_extension_type_id": 1}]})
case("uzanti gecersiz alan", "PUT", "/api/production-lines/{L}/stages/{SC}/extensions",
     {"extensions": [{"stage_extension_type_id": 1, "values": [{"field_key": "yok", "value_text": "x"}]}]})
case("uzanti ekle", "PUT", "/api/production-lines/{L}/stages/{SC}/extensions",
     {"extensions": [{"stage_extension_type_id": 2, "is_required": True, "input_production_product_ids": [1, "{P}"]},
                     {"stage_extension_type_id": 1, "sort_order": 7, "output_production_product_ids": [3]}]})
case("uzanti baska hattin asamasi", "PUT", "/api/production-lines/{L}/stages/1/extensions",
     {"extensions": [{"stage_extension_type_id": 1, "input_production_product_ids": [1]}]})
case("hat goster", "GET", "/api/production-lines/{L}")
case("tip akisi", "GET", "/api/production-product-types/{T}/flow")

# ---- temizlik ----------------------------------------------------------
case("asamali hatti sil", "DELETE", "/api/production-lines/{L}")
case("tipi sil", "DELETE", "/api/production-product-types/{T}")
case("kategori sil", "DELETE", "/api/production-products/{P}")
case("prosesi sil", "DELETE", "/api/process-types/{PT}")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({"source": "Laravel " + BASE + ", dondurulmus referans veri", "cases": cases},
                          ensure_ascii=False, indent=1), encoding="utf-8")
print(f"\n{len(cases)} durum -> {OUT}")
