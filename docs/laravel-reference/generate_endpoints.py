"""Laravel route:list JSON + controller kaynagindan endpoint envanteri uretir.

Kullanim: python -I docs/laravel-reference/generate_endpoints.py docs/laravel-reference/routes.json <laravel_app_dir> docs/laravel-reference/ENDPOINTS.md
(routes.json: konteynerde `php artisan route:list --json --except-vendor`)
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

routes_path, app_dir, out_path = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
routes = json.loads(Path(routes_path).read_text(encoding="utf-8"))

# Controller -> (faz, modul)
PHASES = {
    "AuthController": (2, "identity"), "UserController": (2, "identity"),
    "RoleController": (2, "identity"), "PermissionController": (2, "identity"),
    "UserRoleController": (2, "identity"),
    "AppReleaseController": (3, "app-release"),
    "ProductionProductTypeController": (4, "catalog"), "ProductionProductController": (4, "catalog"),
    "ProductionProductAttributeDefinitionController": (4, "catalog"),
    "AttributeDefinitionController": (4, "catalog"),
    "ProcessTypeController": (4, "catalog"), "ProcessTypeFieldController": (4, "catalog"),
    "StageExtensionTypeController": (4, "catalog"), "ProductionLineController": (4, "catalog"),
    "ProductionStageController": (4, "catalog"), "ProductionStageExtensionController": (4, "catalog"),
    "SupplierController": (4, "catalog"), "ProductSupplierController": (4, "catalog"),
    "StorageUnitController": (5, "inventory"), "MaterialController": (5, "inventory"),
    "StorageMovementController": (5, "inventory"),
    "ProductionBatchController": (6, "production"), "BatchProcessController": (6, "production"),
    "PackagingItemController": (7, "packaging"), "PackagingLotController": (7, "packaging"),
    "FinishedProductRecipeController": (8, "finished-product"),
    "FinishedProductController": (8, "finished-product"),
    "FinishedProductRunController": (8, "finished-product"),
    "FinishedProductStorageController": (8, "finished-product"),
    "CustomerController": (9, "sales"), "OrderController": (9, "sales"),
    "NotificationController": (10, "notification"),
    "AnalyticsController": (11, "analytics"), "DailySummaryController": (11, "analytics"),
}


def method_source(controller: str, method: str) -> str:
    path = app_dir / "app/Http/Controllers/Api" / f"{controller}.php"
    if not path.exists():
        return ""
    src = path.read_text(encoding="utf-8")
    m = re.search(rf"function\s+{method}\s*\(", src)
    if not m:
        return ""
    # Bir sonraki public/protected/private function'a kadar
    nxt = re.search(r"\n\s*(public|protected|private)\s+function\s", src[m.end():])
    return src[m.start(): m.end() + (nxt.start() if nxt else len(src))]


def describe(body: str):
    sig = body.split("{", 1)[0]
    req = re.search(r"\(\s*(?:[^,)]*,\s*)*?(\w+Request)\s+\$", sig)
    request = req.group(1) if req and req.group(1) != "Request" else ""
    res = re.findall(r"(\w+Resource)::collection|new\s+(\w+Resource)", body)
    names = []
    for coll, single in res:
        n = f"{coll}[]" if coll else single
        if n not in names:
            names.append(n)
    if names:
        response = ", ".join(names)
    elif "->download(" in body:
        response = "dosya"
    elif "response()->json" in body or "JsonResponse" in sig:
        response = "json"
    else:
        response = "?"
    return request, response


rows = defaultdict(list)
unmapped = []
for r in routes:
    uri = r["uri"]
    if not uri.startswith("api/"):
        continue
    action = r["action"]
    mw = r["middleware"]
    auth = "sanctum" if any("Authenticate:sanctum" in m for m in mw) else "-"
    perms = [m.split(":", 1)[1] for m in mw if "PermissionMiddleware:" in m]
    roles = ["role:" + m.split(":", 1)[1] for m in mw if "RoleMiddleware:" in m]
    throttle = [m.split(":", 1)[1] for m in mw if "ThrottleRequests" in m and ":" in m]
    access = ", ".join(perms + roles) or ("giriş yeterli" if auth != "-" else "herkese açık")
    if throttle:
        access += f" (throttle: {', '.join(throttle)})"
    method = r["method"].replace("|HEAD", "")
    if "@" in action:
        ctrl, meth = action.rsplit("\\", 1)[-1].split("@")
        request, response = describe(method_source(ctrl, meth))
        handler = f"{ctrl}@{meth}"
    else:
        ctrl, handler, request, response = "Closure", "Closure", "", "?"
        if uri == "api/user":
            ctrl, response = "AuthController", "User modeli (ham, $hidden hariç)"
    phase = PHASES.get(ctrl)
    if not phase:
        unmapped.append((method, uri, handler))
        phase = (99, "atanmamış")
    rows[phase].append((uri, method, handler, access, request, response))

lines = [
    "# Laravel API Envanteri",
    "",
    "`php artisan route:list --json` çıktısından ve controller kaynak kodundan otomatik üretildi.",
    "Kaynak: `Olive_Production_Tracking_App_Back` `dev` branch'i, `ba61990`. Ham veri: [routes.json](routes.json)",
    "",
    "- **Erişim:** Spatie izni ya da rolü. \"giriş yeterli\" = sadece Sanctum token'ı gerekir.",
    "- **Request:** validation'ı yapan FormRequest sınıfı (`app/Http/Requests`).",
    "- **Cevap:** kullanılan Resource sınıfı (`[]` = koleksiyon, sayfalıysa `data/links/meta`). "
    "`json` = elle yazılmış `response()->json`, Java'da birebir kopyalanmalı.",
    "",
    "## Özet",
    "",
    "| Faz | Modül | Endpoint |",
    "|---|---|---|",
]
total = 0
for (ph, mod) in sorted(rows):
    lines.append(f"| {ph if ph != 99 else '-'} | {mod} | {len(rows[(ph, mod)])} |")
    total += len(rows[(ph, mod)])
lines.append(f"| | **Toplam** | **{total}** |")
lines.append("")

for (ph, mod) in sorted(rows):
    title = f"Faz {ph}: {mod}" if ph != 99 else "Fazı atanmamış"
    lines += [f"## {title}", "", "| Metot | URI | Handler | Erişim | Request | Cevap |", "|---|---|---|---|---|---|"]
    for uri, method, handler, access, request, response in sorted(rows[(ph, mod)]):
        lines.append(f"| {method} | `/{uri}` | {handler} | {access} | {request or '-'} | {response} |")
    lines.append("")

out_path.write_text("\n".join(lines), encoding="utf-8")
print(f"toplam={total}")
for k in sorted(rows):
    print(k, len(rows[k]))
print("atanmamis:", unmapped)
