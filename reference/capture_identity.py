"""Faz 2 (identity) icin Laravel'in davranisini yakalar: validation, yetki, ozel hatalar.

Veri degistirir. Calistirmadan once ve sonra: reference/restore.sh
Cikti: src/test/resources/laravel/identity.json (Java testleri bu cevaplari bekler).

  python -I reference/capture_identity.py [base_url]
"""
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8001"
OUT = Path(__file__).resolve().parent.parent / "src/test/resources/laravel/identity.json"


def call(method, path, token=None, body=None, raw=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Accept", "application/json")
    if data is not None:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as x:
            status, headers, text = x.status, dict(x.headers), x.read().decode()
    except urllib.error.HTTPError as e:
        status, headers, text = e.code, dict(e.headers), e.read().decode()
    try:
        parsed = json.loads(text) if text else None
    except ValueError:
        parsed = text[:200]
    keep = {k: v for k, v in headers.items() if k.lower().startswith(("x-ratelimit", "retry-after"))}
    return status, keep, parsed


def login(email):
    return call("POST", "/api/auth/login", body={"email": email, "password": "123"})[2]["token"]


cases = []


def case(name, method, path, token=None, body=None, raw=None, who=None):
    status, headers, parsed = call(method, path, token, body, raw)
    cases.append({"name": name, "as": who, "method": method, "path": path, "body": body,
                  "status": status, "headers": headers, "response": parsed})
    print(f"{status} {name}")
    return parsed


admin = login("admin@olivamore.com")
operator = login("operator1@olivamore.com")

# --- kimlik dogrulama ---
case("token yok", "GET", "/api/auth/me")
case("bozuk token", "GET", "/api/auth/me", token="abc")
case("id'li bozuk token", "GET", "/api/auth/me", token="1|yanlis")
case("me", "GET", "/api/auth/me", token=operator, who="operator1")
case("api/user", "GET", "/api/user", token=operator, who="operator1")

# --- yetki ---
case("operator users listesi", "GET", "/api/users", token=operator, who="operator1")
case("operator olmayan kullanici", "GET", "/api/users/999999", token=operator, who="operator1")
case("admin olmayan kullanici", "GET", "/api/users/999999", token=admin, who="admin")
case("operator izin gerektiren uc", "GET", "/api/customers", token=operator, who="operator1")
case("tokensiz izin gerektiren uc", "GET", "/api/customers")

# --- login validation ---
case("login bos", "POST", "/api/auth/login", body={})
case("login email sayi", "POST", "/api/auth/login", body={"email": 5, "password": 5})
case("login email gecersiz", "POST", "/api/auth/login", body={"email": "abc", "password": "x"})
case("login email null", "POST", "/api/auth/login", body={"email": None, "password": None})
case("login email dizi", "POST", "/api/auth/login", body={"email": ["a@b.c"], "password": "x"})
case("login yanlis sifre", "POST", "/api/auth/login", body={"email": "admin@olivamore.com", "password": "yanlis"})
case("login olmayan kullanici", "POST", "/api/auth/login", body={"email": "yok@olivamore.com", "password": "x"})

# --- register validation ---
case("register bos", "POST", "/api/auth/register", body={})
case("register hatali", "POST", "/api/auth/register",
     body={"name": 5, "email": "admin@olivamore.com", "password": "kisa", "password_confirmation": "farkli"})
case("register uzun", "POST", "/api/auth/register",
     body={"name": "x" * 256, "email": "a" * 250 + "@b.co", "password": "uzun-sifre-1", "password_confirmation": "uzun-sifre-1"})
case("register basarili", "POST", "/api/auth/register",
     body={"name": "  Yeni Kullanici  ", "email": "yeni@olivamore.com", "password": " bosluklu-sifre ", "password_confirmation": " bosluklu-sifre "})
case("register sonrasi bosluklu sifreyle login", "POST", "/api/auth/login",
     body={"email": "yeni@olivamore.com", "password": " bosluklu-sifre "})

# --- kullanici yonetimi (admin) ---
case("users store bos", "POST", "/api/users", token=admin, body={}, who="admin")
case("users store hatali roller", "POST", "/api/users", token=admin, who="admin",
     body={"name": "A", "email": "a@b.co", "password": "12345678", "roles": [5, "yok", "planner"]})
case("users store roller dizi degil", "POST", "/api/users", token=admin, who="admin",
     body={"name": "A", "email": "a@b.co", "password": "12345678", "roles": "planner"})
created = case("users store", "POST", "/api/users", token=admin, who="admin",
               body={"name": "Test Kullanici", "email": "test@olivamore.com", "password": "12345678", "roles": ["planner", "operator"]})
uid = created["data"]["id"]
case("users update rolsuz", "PUT", f"/api/users/{uid}", token=admin, who="admin",
     body={"name": "Test Kullanici 2", "email": "test@olivamore.com"})
case("users update roller null", "PUT", f"/api/users/{uid}", token=admin, who="admin",
     body={"name": "Test Kullanici 2", "email": "test@olivamore.com", "roles": None})
case("users update email baskasinin", "PUT", f"/api/users/{uid}", token=admin, who="admin",
     body={"name": "X", "email": "admin@olivamore.com"})
case("users update kisa sifre", "PATCH", f"/api/users/{uid}", token=admin, who="admin",
     body={"name": "X", "email": "test@olivamore.com", "password": "kisa"})
case("user roles bos", "PATCH", f"/api/users/{uid}/roles", token=admin, body={"roles": []}, who="admin")
case("user roles", "PATCH", f"/api/users/{uid}/roles", token=admin, body={"roles": ["food_engineer"]}, who="admin")
case("admin'den admin rolu kaldirma", "PATCH", "/api/users/1/roles", token=admin, body={"roles": ["planner"]}, who="admin")
case("admin update admin rolsuz", "PUT", "/api/users/1", token=admin, who="admin",
     body={"name": "Olivamore Admin", "email": "admin@olivamore.com", "roles": ["planner"]})
case("admin silme", "DELETE", "/api/users/1", token=admin, who="admin")
case("users delete", "DELETE", f"/api/users/{uid}", token=admin, who="admin")
case("users delete tekrar", "DELETE", f"/api/users/{uid}", token=admin, who="admin")

# --- rol yonetimi (admin) ---
case("roles store bos", "POST", "/api/roles", token=admin, body={}, who="admin")
case("roles store var olan", "POST", "/api/roles", token=admin, body={"name": "planner", "permissions": ["yok.izin"]}, who="admin")
# Laravel hatasi: API'den olusan rol guard_name='sanctum' aliyor, izinler 'web' -> 500 ve rol
# yine de kaydediliyor. Java duzeltir (bkz. DEVIATIONS.md); burada Laravel'in davranisi kayit icin.
case("roles store izinli (Laravel 500)", "POST", "/api/roles", token=admin, who="admin",
     body={"name": "deneme_izinli", "permissions": ["customer.view", "order.view"]})
role = case("roles store", "POST", "/api/roles", token=admin, who="admin", body={"name": "deneme_rolu"})
rid = role["data"]["id"]
case("roles update izinsiz", "PUT", f"/api/roles/{rid}", token=admin, body={"name": "deneme_rolu_2"}, who="admin")
case("admin rolu yeniden adlandirma", "PUT", "/api/roles/1", token=admin, body={"name": "yonetici"}, who="admin")
case("admin rolu silme", "DELETE", "/api/roles/1", token=admin, who="admin")
case("roles delete", "DELETE", f"/api/roles/{rid}", token=admin, who="admin")

# --- cikis ---
case("logout", "POST", "/api/auth/logout", token=operator, who="operator1")
case("logout sonrasi ayni token", "GET", "/api/auth/me", token=operator, who="operator1")

# --- hiz siniri (en sonda: sayaclar dolar) ---
for i in range(5):
    case(f"login sinir {i + 1}", "POST", "/api/auth/login", body={"email": "sinir@olivamore.com", "password": "x"})
case("login sinir asildi", "POST", "/api/auth/login", body={"email": "Sinir@olivamore.com", "password": "x"})

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({"source": "Laravel " + BASE + ", dondurulmus referans veri", "cases": cases},
                          ensure_ascii=False, indent=1), encoding="utf-8")
print(f"\n{len(cases)} durum -> {OUT}")
