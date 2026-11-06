"""Laravel API'sinin GET cevaplarini referans olarak kaydeder.

Dondurulmus referans veri (reference-data.sql) yuklu bir Laravel'e karsi calisir.
Her GET endpoint'i admin olarak cagrilir, cevap reference/responses/ altina yazilir.
Java tarafi ayni istekleri atip bu dosyalarla karsilastiracak.

Kullanim:
  python -I reference/capture_responses.py <laravel_compose_dir> [base_url]
"""
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROUTES = HERE.parent / "docs/laravel-reference/routes.json"
OUT = HERE / "responses"

compose_dir = sys.argv[1]
base = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8001"

ADMIN = {"email": "admin@olivamore.com", "password": "123"}

# Demo veri seed gunune gore son bir yila yayiliyor (2025-11-06 .. 2026-11-06).
RANGE = "from=2025-11-01&to=2026-11-06"
EXTRA = [
    ("api/analytics/customers", RANGE, "range"),
    ("api/analytics/customers/{customer}", RANGE, "range"),
    ("api/analytics/suppliers", RANGE, "range"),
    ("api/analytics/yield", RANGE, "range"),
    ("api/analytics/query", RANGE + "&metric=oil_kg&group=time", "oil_kg_time"),
    ("api/analytics/query", RANGE + "&metric=incoming_kg&group=supplier", "incoming_kg_supplier"),
    ("api/production-batches/calendar", RANGE, "range"),
]


def sql(query: str) -> str:
    out = subprocess.run(
        ["docker", "compose", "exec", "-T", "db", "sh", "-c",
         'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$0"', query],
        cwd=compose_dir, capture_output=True, text=True, check=True,
    )
    return out.stdout.strip()


def first_id(table: str, where: str = "") -> str:
    return sql(f"select id from {table} {where} order by id limit 1")


# Path parametresi -> deger. Ic ice olanlar ust kaydin cocugundan secilir.
def resolve_params() -> dict:
    p = {}
    simple = {
        "customer": "customers", "batchProcess": "batch_processes",
        "finishedProductRecipe": "finished_product_recipes",
        "finished_product_recipe": "finished_product_recipes",
        "finishedProductRun": "finished_product_runs",
        "finishedProductStorage": "finished_product_storages",
        "finished_product_storage": "finished_product_storages",
        "finished_product": "finished_products", "material": "materials",
        "order": "orders", "packaging_item": "packaging_items",
        "packaging_lot": "packaging_lots", "product_supplier": "product_suppliers",
        "production_batch": "production_batches", "productionProductType": "production_product_types",
        "role": "roles", "storageUnit": "storage_units", "storage_unit": "storage_units",
        "supplier": "suppliers", "user": "users",
    }
    for name, table in simple.items():
        p[name] = first_id(table)
    # Ic ice: once cocugu olan bir ust kayit secilir
    p["process_type"] = sql("select process_type_id from process_type_fields order by process_type_id, id limit 1")
    p["field"] = first_id("process_type_fields", f"where process_type_id = {p['process_type']}")
    p["production_line"] = sql("select production_line_id from production_stages order by production_line_id, id limit 1")
    p["stage"] = first_id("production_stages", f"where production_line_id = {p['production_line']}")
    p["productionProduct"] = sql(
        "select production_product_id from production_product_attribute_definitions order by production_product_id, id limit 1")
    p["attributeDefinition"] = first_id(
        "production_product_attribute_definitions", f"where production_product_id = {p['productionProduct']}")
    return p


def request(method: str, url: str, token: str | None = None, body: dict | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()


def main():
    status, _, raw = request("POST", f"{base}/api/auth/login", body=ADMIN)
    if status != 200:
        sys.exit(f"login basarisiz: {status} {raw[:200]!r}")
    token = json.loads(raw)["token"]

    params = resolve_params()
    routes = json.loads(ROUTES.read_text(encoding="utf-8"))
    gets = sorted(r["uri"] for r in routes if r["uri"].startswith("api/") and "GET" in r["method"])

    OUT.mkdir(exist_ok=True)
    index = []
    for uri in gets:
        path = re.sub(r"\{(\w+)\??\}", lambda m: params[m.group(1)], uri)
        status, ctype, raw = request("GET", f"{base}/{path}", token)
        if "json" in ctype:
            body = json.loads(raw) if raw else None
            kind = "json"
        else:
            body = f"<{ctype or 'bilinmiyor'}, {len(raw)} bayt>"
            kind = "binary"
        slug = re.sub(r"[^A-Za-z0-9]+", "_", uri.removeprefix("api/")).strip("_") or "root"
        (OUT / f"GET_{slug}.json").write_text(
            json.dumps({"method": "GET", "route": uri, "path": "/" + path, "status": status,
                        "content_type": ctype, "body": body}, ensure_ascii=False, indent=1),
            encoding="utf-8")
        index.append((uri, path, status, kind))
        print(f"{status} /{path}")

    # Zorunlu query parametresi isteyen endpoint'ler: parametresiz hali (422,
    # validation formati referansi) yukarida kaydedildi, burada parametreli hali.
    for uri, query, suffix in EXTRA:
        path = re.sub(r"\{(\w+)\??\}", lambda m: params[m.group(1)], uri) + "?" + query
        status, ctype, raw = request("GET", f"{base}/{path}", token)
        body = json.loads(raw) if raw and "json" in ctype else f"<{ctype}, {len(raw)} bayt>"
        slug = re.sub(r"[^A-Za-z0-9]+", "_", uri.removeprefix("api/")).strip("_") + "__" + suffix
        (OUT / f"GET_{slug}.json").write_text(
            json.dumps({"method": "GET", "route": uri, "path": "/" + path, "status": status,
                        "content_type": ctype, "body": body}, ensure_ascii=False, indent=1),
            encoding="utf-8")
        index.append((uri, path, status, "json"))
        print(f"{status} /{path}")

    (OUT / "_params.json").write_text(json.dumps(params, indent=1), encoding="utf-8")
    bad = [i for i in index if i[2] >= 400]
    print(f"\n{len(index)} istek, {len(bad)} tanesi 4xx/5xx")
    for uri, path, st, _ in bad:
        print(f"  {st} /{path}")


main()
