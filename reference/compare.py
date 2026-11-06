"""Laravel ve Java'nin ayni isteklere verdigi cevaplari karsilastirir.

Iki sistem ayni veritabanina bakarken AYNI ANDA istek atilir (zamana bagli cevaplar
yuzunden kaydedilmis dosyalarla degil, canli karsilastirma). Token Laravel'den alinir;
Faz 2'den sonra Java ayni Sanctum token'ini dogrular.

Karsilastirma JSON duzeyindedir: bosluk, "\\/" kacisi ve \\u kodlari onemsizdir.
Normallestirmeler:
  - Iki sistemin adresleri (sayfalama linkleri) {BASE} ile degistirilir
  - --unordered ile verilen yollardaki diziler sirasiz karsilastirilir
    (ornek: rollerin izin listesi Laravel'de rastgele sirada geliyor)

Kullanim:
  python -I reference/compare.py <laravel_compose_dir> [secenekler]

  --laravel URL      varsayilan http://localhost:8001
  --java URL         varsayilan http://localhost:8081
  --only PREFIX      yalnizca bu onekle baslayan route'lar (tekrarlanabilir), ornek: --only api/app
  --path YOL         route listesi yerine dogrudan bu istek (tekrarlanabilir), ornek: --path "/api/customers?per_page=2"
  --unordered YOL    sirasiz karsilastirilacak dizi yolu, * joker (varsayilan: rol izinleri)
  --no-auth          token gonderme
  --max-diffs N      her istek icin gosterilecek en fazla fark (varsayilan 10)

Cikis kodu: tum istekler ayniysa 0, degilse 1.
"""
import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# capture_responses.py'deki parametre cozme ve istek fonksiyonlari yeniden kullanilir
_spec = importlib.util.spec_from_file_location("capture_responses", HERE / "capture_responses.py")
cap = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cap)

DEFAULT_UNORDERED = ["data.*.permissions", "data.permissions", "data.*.roles.*.permissions", "data.roles.*.permissions"]


def normalize(value, bases):
    if isinstance(value, str):
        for b in bases:
            value = value.replace(b, "{BASE}")
        return value
    if isinstance(value, list):
        return [normalize(v, bases) for v in value]
    if isinstance(value, dict):
        return {k: normalize(v, bases) for k, v in value.items()}
    return value


def path_matches(path: str, patterns) -> bool:
    for pattern in patterns:
        regex = "^" + re.escape(pattern).replace(r"\*", r"[^.]+") + "$"
        if re.match(regex, path):
            return True
    return False


def diff(a, b, path, unordered, out):
    """a: Laravel, b: Java. Farklari (yol, laravel, java) olarak out'a ekler."""
    if isinstance(a, dict) and isinstance(b, dict):
        for k in a.keys() | b.keys():
            p = f"{path}.{k}" if path else k
            if k not in b:
                out.append((p, a[k], "<yok>"))
            elif k not in a:
                out.append((p, "<yok>", b[k]))
            else:
                diff(a[k], b[k], p, unordered, out)
        return
    if isinstance(a, list) and isinstance(b, list):
        if path_matches(path, unordered):
            key = lambda v: json.dumps(v, sort_keys=True, ensure_ascii=False)
            if sorted(map(key, a)) != sorted(map(key, b)):
                out.append((path + " (sirasiz)", a, b))
            return
        if len(a) != len(b):
            out.append((path + " (uzunluk)", len(a), len(b)))
        for i, (x, y) in enumerate(zip(a, b)):
            diff(x, y, f"{path}.{i}", unordered, out)
        return
    # PHP 1 ile 1.0'i ayni yazar; tip farki (bool/int) yine de farktir
    if type(a) is not type(b) and not (isinstance(a, (int, float)) and isinstance(b, (int, float))
                                       and not isinstance(a, bool) and not isinstance(b, bool)):
        out.append((path + " (tip)", a, b))
    elif a != b:
        out.append((path, a, b))


def fetch(base, path, token):
    status, ctype, raw = cap.request("GET", base + path, token)
    if "json" in ctype:
        return status, json.loads(raw) if raw else None
    return status, f"<{ctype}, {len(raw)} bayt>"


def short(v, n=120):
    s = json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else repr(v)
    return s if len(s) <= n else s[:n] + "..."


def main():
    ap = argparse.ArgumentParser(description="Laravel / Java cevap karsilastirma")
    ap.add_argument("compose_dir")
    ap.add_argument("--laravel", default="http://localhost:8001")
    ap.add_argument("--java", default="http://localhost:8081")
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--path", action="append", default=[])
    ap.add_argument("--unordered", action="append", default=None)
    ap.add_argument("--no-auth", action="store_true")
    ap.add_argument("--max-diffs", type=int, default=10)
    args = ap.parse_args()

    cap.compose_dir = args.compose_dir
    unordered = args.unordered if args.unordered is not None else DEFAULT_UNORDERED

    token = None
    if not args.no_auth:
        status, _, raw = cap.request("POST", f"{args.laravel}/api/auth/login", body=cap.ADMIN)
        if status != 200:
            sys.exit(f"Laravel login basarisiz: {status}")
        token = json.loads(raw)["token"]

    if args.path:
        paths = args.path
    else:
        params = cap.resolve_params()
        routes = json.loads(cap.ROUTES.read_text(encoding="utf-8"))
        uris = sorted(r["uri"] for r in routes if r["uri"].startswith("api/") and "GET" in r["method"])
        if args.only:
            uris = [u for u in uris if any(u.startswith(o.lstrip("/")) for o in args.only)]
        paths = ["/" + re.sub(r"\{(\w+)\??\}", lambda m: params[m.group(1)], u) for u in uris]
        paths += ["/" + uri + "?" + query for uri, query, _ in cap.EXTRA
                  if not args.only or any(uri.startswith(o.lstrip("/")) for o in args.only)]

    bases = [args.laravel, args.java]
    failed = 0
    for path in paths:
        ls, lb = fetch(args.laravel, path, token)
        js, jb = fetch(args.java, path, token)
        diffs = []
        if ls != js:
            diffs.append(("<status>", ls, js))
        diff(normalize(lb, bases), normalize(jb, bases), "", unordered, diffs)
        if diffs:
            failed += 1
            print(f"FARKLI  {path}  (Laravel {ls}, Java {js}, {len(diffs)} fark)")
            for p, a, b in diffs[:args.max_diffs]:
                print(f"    {p or '<kok>'}\n      laravel: {short(a)}\n      java:    {short(b)}")
            if len(diffs) > args.max_diffs:
                print(f"    ... {len(diffs) - args.max_diffs} fark daha")
        else:
            print(f"AYNI    {path}  ({ls})")

    print(f"\n{len(paths)} istek: {len(paths) - failed} ayni, {failed} farkli")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
