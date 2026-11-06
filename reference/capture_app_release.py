"""Faz 3 (app-release) icin Laravel'in davranisini yakalar.

Laravel'in surum klasorune (APP_RELEASES_PATH, compose'da ./docker-data -> /data, yani
olive_oil_back/docker-data/app) her senaryo icin dosyalar yazilir, istekler atilir.
Rate limit 30/dk iki endpoint icin ortak: senaryolar bir dakika icinde 30 istegin altinda
kalir, en sonda sinir doldurulur. Calistirmadan once en az 60 sn bu endpoint'lere istek atilmamis olmali.

Cikti: src/test/resources/laravel/app-release.json (Java'da AppReleaseParityIT ayni sirayla oynatir).

  python -I reference/capture_app_release.py <laravel_compose_dir> [base_url]
"""
import base64
import json
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

compose_dir = Path(sys.argv[1])
BASE = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8001"
RELEASES = compose_dir / "docker-data" / "app"
OUT = Path(__file__).resolve().parent.parent / "src/test/resources/laravel/app-release.json"

HEADERS = ("content-type", "content-disposition", "content-length", "accept-ranges", "content-range",
           "cache-control", "last-modified", "x-ratelimit-limit", "x-ratelimit-remaining", "retry-after")

APK = "fake-apk-7"
NORMAL = {"version_code": 7, "version_name": "1.0.6", "notes": "Yeni rapor olusturucu",
          "sha256": "6a40d3b9a3b2d2f4e7d1e2d0b7a7f3c4e5d6f7a8b9c0d1e2f3a4b5c6d7e8f9a0", "size": len(APK),
          "published_at": "2026-09-19T10:00:00Z", "file": "olivamore.apk"}


def call(method, path, headers=None):
    r = urllib.request.Request(BASE + path, method=method)
    r.add_header("Accept", "application/json")
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r) as x:
            status, hdrs, raw = x.status, x.headers, x.read()
    except urllib.error.HTTPError as e:
        status, hdrs, raw = e.code, e.headers, e.read()
    keep = {k.lower(): v for k, v in hdrs.items() if k.lower() in HEADERS}
    if "json" in (hdrs.get("Content-Type") or ""):
        body = {"json": json.loads(raw) if raw else None}
    else:
        body = {"base64": base64.b64encode(raw).decode()}
    return status, keep, body


def files(spec):
    """spec: {dosya adi: metin} - klasor bastan kurulur."""
    shutil.rmtree(RELEASES, ignore_errors=True)
    if spec is None:
        return
    RELEASES.mkdir(parents=True)
    for name, content in spec.items():
        data = content.encode("utf-8") if isinstance(content, str) else content
        (RELEASES / name).write_bytes(data)


scenarios = []


def scenario(name, spec, requests):
    files(spec)
    results = []
    for method, path, headers in requests:
        status, hdrs, body = call(method, path, headers)
        results.append({"method": method, "path": path, "headers": headers or {}, "status": status,
                        "response_headers": hdrs, "body": body})
        print(f"{status} {name}: {method} {path} {headers or ''}")
    scenarios.append({"name": name, "files": {k: v if isinstance(v, str) else None for k, v in (spec or {}).items()},
                      "requests": results})


V = ("GET", "/api/app/version", None)
D = ("GET", "/api/app/download", None)

scenario("surum yok", None, [V, D])
scenario("normal", {"version.json": json.dumps(NORMAL), "olivamore.apk": APK},
         [V, D, ("GET", "/api/app/download", {"Range": "bytes=0-3"}), ("HEAD", "/api/app/download", None)])
scenario("php donusumleri 1", {"version.json": json.dumps(
    {"version_code": "7abc", "version_name": 106, "size": "12.9", "notes": 5, "sha256": None})}, [V])
scenario("php donusumleri 2", {"version.json": json.dumps(
    {"version_code": 7.9, "version_name": True, "size": True, "notes": ["a", "b"], "published_at": {"x": 1}})}, [V])
scenario("surum adi yok", {"version.json": json.dumps({"version_code": 7})}, [V])
scenario("surum adi null", {"version.json": json.dumps({"version_code": 7, "version_name": None})}, [V])
scenario("bozuk json", {"version.json": "{bozuk", "olivamore.apk": APK}, [V, D])
scenario("json dizi", {"version.json": json.dumps([7, "1.0.6"])}, [V])
scenario("bom", {"version.json": "﻿" + json.dumps(NORMAL), "olivamore.apk": APK}, [V])
scenario("klasor disi", {"version.json": json.dumps(dict(NORMAL, file="../../../.env")), "olivamore.apk": APK}, [D])
scenario("ters bolu", {"version.json": json.dumps(dict(NORMAL, file="..\\..\\olivamore.apk")), "olivamore.apk": APK}, [D])
scenario("alt klasor adi", {"version.json": json.dumps(dict(NORMAL, file="sub/olivamore.apk")), "olivamore.apk": APK}, [D])
scenario("apk yok", {"version.json": json.dumps(NORMAL)}, [D])
scenario("dosya adi yok", {"version.json": json.dumps({k: v for k, v in NORMAL.items() if k != "file"}), "olivamore.apk": APK}, [D])
scenario("bosluklu surum adi", {"version.json": json.dumps(dict(NORMAL, version_name="1.0 beta")), "olivamore.apk": APK}, [D])
scenario("turkce surum adi", {"version.json": json.dumps(dict(NORMAL, version_name="1.0-şık")), "olivamore.apk": APK}, [D])

# Buraya kadar 21 istek. Ortak 30'luk sinir: 9 istek daha gecer, sonraki iki uc da 429.
scenario("sinir", {"version.json": json.dumps(NORMAL), "olivamore.apk": APK}, [V] * 9 + [V, D])

files(None)
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({"source": "Laravel " + BASE, "apk": APK, "scenarios": scenarios}, ensure_ascii=False, indent=1),
               encoding="utf-8")
print(f"\n{sum(len(s['requests']) for s in scenarios)} istek -> {OUT}")
