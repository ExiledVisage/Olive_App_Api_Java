"""Faz 9a (musteriler) icin Laravel'in davranisini yakalar: musteri, adres, formul tercihleri ve profili.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/customer.json (Java'da CustomerParityIT).

  python -I reference/capture_customer.py <laravel_compose_dir> [base_url]

Kullanicilar: admin, planner (musteri yonetimi), muhendis (goruntuleme), operator1 (yetkisiz).
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
PLANNER = "planner@olivamore.com"
ENGINEER = "muhendis@olivamore.com"
OPERATOR = "operator1@olivamore.com"
C = "/api/customers"

# ---- okuma ---------------------------------------------------------------
for q in ["", "?is_active=1", "?is_active=0", "?per_page=5", "?per_page=5&page=2", "?per_page=0"]:
    c("liste " + (q or "varsayilan"), "GET", C + q)
for n in [1, 2, 5, 16]:
    c(f"detay {n}", "GET", f"{C}/{n}")
    c(f"adresler {n}", "GET", f"{C}/{n}/addresses")
    c(f"formul profili {n}", "GET", f"{C}/{n}/recipes")
c("olmayan", "GET", C + "/999999")
c("liste muhendis", "GET", C, as_user=ENGINEER)
c("liste yetkisiz", "GET", C, as_user=OPERATOR)

# ---- musteri ---------------------------------------------------------------
c("ekle bos", "POST", C, {})
c("ekle hatali", "POST", C, {"name": "", "email": "x", "phone": "1" * 31, "is_active": "evet",
                             "addresses": [{"label": "", "latitude": 95, "longitude": "abc", "is_default": "x"}]})
c("ekle", "POST", C, {"name": "Test Musteri"}, ("C1", "data.id"))
c("ekle adresli", "POST", C, {"name": "Adresli Musteri", "company_name": "Adresli A.S.", "email": "a@b.test", "is_active": None,
                              "addresses": [{"label": "Merkez", "city": "Izmir", "latitude": 38.4237, "longitude": "27.1428",
                                             "is_default": True},
                                            {"label": "Depo", "city": "Manisa", "country": None, "is_default": "1"},
                                            {"label": "Sube", "city": "Aydin", "country": "GR"}]}, ("C2", "data.id"))
c("ekle planlamaci", "POST", C, {"name": "Planlamaci Musterisi", "is_active": False}, ("C3", "data.id"), as_user=PLANNER)
c("ekle muhendis", "POST", C, {"name": "x"}, as_user=ENGINEER)
c("guncelle", "PATCH", C + "/{C1}", {"name": "Test Musteri 2", "notes": "not", "is_active": "0"})
c("guncelle put", "PUT", C + "/{C1}", {"phone": "0555"})
c("guncelle hatali", "PATCH", C + "/{C1}", {"name": None, "email": "x"})
c("detay yeni", "GET", C + "/{C2}")

# ---- adres -------------------------------------------------------------------
A = C + "/{C2}/addresses"
c("adres ekle bos", "POST", A, {})
c("adres ekle", "POST", A, {"label": "Yeni", "city": "Bursa", "latitude": 40, "longitude": "29.06"}, ("A1", "data.id"))
c("adres ekle varsayilan", "POST", A, {"label": "Varsayilan", "city": "Ankara", "is_default": 1, "country": "TR"}, ("A2", "data.id"))
c("adres ekle hassas", "POST", A, {"label": "Hassas", "city": "Van", "latitude": "38.123456789", "is_default": False})
c("adresler sonra", "GET", A)
c("adres guncelle", "PATCH", A + "/{A1}", {"label": "Yeni 2", "is_default": "1", "latitude": None})
c("adres guncelle hatali", "PATCH", A + "/{A1}", {"label": None, "city": "", "longitude": 200})
c("adres guncelle baska musteri", "PATCH", C + "/1/addresses/{A1}", {"label": "x"})
c("adres guncelle baska musteri hatali", "PATCH", C + "/1/addresses/{A1}", {"label": None})
c("adres guncelle olmayan", "PATCH", A + "/999999", {"label": "x"})
c("adres sil baska musteri", "DELETE", C + "/1/addresses/{A1}")
c("adres sil varsayilan", "DELETE", A + "/{A1}")
c("adres sil", "DELETE", A + "/{A2}")
c("adresler son", "GET", A)
cap.sql("siparisli adres", "select delivery_address_id as id from orders where delivery_address_id is not null order by id limit 1",
        save="OA")
cap.sql("siparisli adresin musterisi", "select customer_id as id from customer_addresses where id = {OA}", save="OC")
c("adres sil siparisli", "DELETE", C + "/{OC}/addresses/{OA}")

# ---- formul tercihleri -------------------------------------------------------------
P = C + "/{C2}/preferred-recipes"
c("tercih ekle bos", "POST", P, {})
c("tercih ekle hatali", "POST", P, {"finished_product_recipe_id": 999, "note": "x" * 256})
c("tercih ekle", "POST", P, {"finished_product_recipe_id": 5, "note": "blend sever"})
c("tercih ekle ikinci", "POST", P, {"finished_product_recipe_id": 1})
c("tercih ekle tekrar", "POST", P, {"finished_product_recipe_id": 5, "note": "not degisti"})
c("tercih ekle mevcut musteri", "POST", C + "/2/preferred-recipes", {"finished_product_recipe_id": 3, "note": None})
c("formul profili tercihli", "GET", C + "/{C2}/recipes")
c("formul profili siparisli tercihli", "GET", C + "/2/recipes")
c("tercih sil", "DELETE", P + "/1")
c("tercih sil olmayan", "DELETE", P + "/1")
c("tercih sil olmayan formul", "DELETE", P + "/999999")
c("formul profili sonra", "GET", C + "/{C2}/recipes")

# ---- silme -------------------------------------------------------------------------
c("sil siparisli", "DELETE", C + "/1")
c("sil adresli", "DELETE", C + "/{C2}")
c("sil", "DELETE", C + "/{C1}")
c("sil olmayan", "DELETE", C + "/{C1}")
c("sil planlamaci", "DELETE", C + "/{C3}", as_user=PLANNER)
c("liste sonra", "GET", C + "?per_page=4")

cap.sql("musteri 2 adresi", "select id from customer_addresses where customer_id = 2 order by id limit 1", save="OA2")

# ---- Laravel 500'leri (Java'da alan yokmus gibi, DEVIATIONS 16) --------------------------------------------------------------
c("guncelle aktif null", "PATCH", C + "/2", {"is_active": None})
c("adres ekle varsayilan null", "POST", C + "/2/addresses", {"label": "Null", "city": "Izmir", "is_default": None})
c("adres guncelle ulke null", "PATCH", C + "/2/addresses/{OA2}", {"country": None, "is_default": None})

cap.write(HERE.parent / "src/test/resources/laravel/customer.json")
