"""Faz 8a (tarifler ve bitmis urunler) icin Laravel'in davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/finished-product.json (Java'da FinishedProductParityIT).

  python -I reference/capture_finished_product.py <laravel_compose_dir> [base_url]

Kullanicilar: admin (her yetki), muhendis (tarif ve urun yonetimi), planner (yalnizca goruntuleme),
depo (goruntuleme).
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
ENGINEER = "muhendis@olivamore.com"
PLANNER = "planner@olivamore.com"
R = "/api/finished-product-recipes"
F = "/api/finished-products"

# ---- tarif okuma -------------------------------------------------------
for q in ["", "?name=gemlik", "?name=0", "?name=", "?name=Portakal"]:
    c("tarif liste " + (q or "varsayilan"), "GET", R + q)
for n in [1, 5, 7]:
    c(f"tarif detay {n}", "GET", f"{R}/{n}")
c("tarif olmayan", "GET", R + "/999999")
c("tarif versiyonlar", "GET", R + "/1/versions")
c("tarif versiyonlar izinsiz kullanici", "GET", R + "/1/versions", as_user="uretim@olivamore.com")
c("tarif liste izinsiz", "GET", R, as_user="uretim@olivamore.com")

# ---- tarif ekleme --------------------------------------------------------
c("tarif ekle bos", "POST", R, {})
c("tarif ekle hatali", "POST", R, {"name": 5, "contents": [{"production_product_type_id": 999, "ratio": 2},
                                                           {"ratio": "abc"}],
                                   "attribute_rules": [{"attribute_definition_id": 999, "value": -1, "min_deviation": "x"}]})
c("tarif ekle toplam eksik", "POST", R, {"name": "Eksik", "contents": [{"production_product_type_id": 3, "ratio": 0.5},
                                                                      {"production_product_type_id": 4, "ratio": "0.3333"}]})
c("tarif ekle toplam fazla tam sayi", "POST", R, {"name": "Fazla", "contents": [{"production_product_type_id": 3, "ratio": 1},
                                                                               {"production_product_type_id": 4, "ratio": 1}]})
c("tarif ekle icerik metin", "POST", R, {"name": "Metin", "contents": "abc"})
c("tarif ekle", "POST", R, {"name": "Test Blend", "description": "deneme",
                            "contents": [{"production_product_type_id": 3, "ratio": 0.7}, {"production_product_type_id": 4, "ratio": "0.3"}],
                            "attribute_rules": [{"attribute_definition_id": 1, "value": 0.5, "min_deviation": 0.1}]},
  ("R1", "data.id"))
c("tarif ekle ucte bir", "POST", R, {"name": "Ucte Bir", "contents": [
    {"production_product_type_id": 3, "ratio": 0.3333}, {"production_product_type_id": 4, "ratio": 0.3333},
    {"production_product_type_id": 6, "ratio": 0.3334}]}, ("R2", "data.id"))
c("tarif ekle muhendis", "POST", R, {"name": "Muhendis Tarifi", "contents": [{"production_product_type_id": 3, "ratio": 1}]},
  ("R3", "data.id"), as_user=ENGINEER)
c("tarif ekle planlamaci", "POST", R, {"name": "Planlamaci", "contents": [{"production_product_type_id": 3, "ratio": 1}]},
  as_user=PLANNER)

# ---- tarif guncelleme (yeni versiyon) -----------------------------------------
c("tarif guncelle aciklama", "PATCH", R + "/{R1}", {"description": "v2"}, ("R1v2", "data.id"))
c("tarif guncelle eski versiyon", "PATCH", R + "/{R1}", {"description": "eski"})
c("tarif guncelle icerik", "PUT", R + "/{R1v2}", {"contents": [{"production_product_type_id": 4, "ratio": 1}],
                                                  "attribute_rules": []}, ("R1v3", "data.id"))
c("tarif guncelle toplam hatali", "PATCH", R + "/{R1v3}", {"contents": [{"production_product_type_id": 4, "ratio": 0.9}]})
c("tarif guncelle icerik bos", "PATCH", R + "/{R1v3}", {"contents": []})
c("tarif guncelle oran eksik", "PATCH", R + "/{R1v3}", {"contents": [{"production_product_type_id": 4}]})
c("tarif guncelle ad", "PATCH", R + "/{R1v3}", {"name": "Test Blend Yeni", "attribute_rules": None}, ("R1v4", "data.id"))
c("tarif guncelle aciklama null", "PATCH", R + "/{R1v4}", {"description": None}, ("R1v5", "data.id"))
c("tarif versiyonlar eski ad", "GET", R + "/{R1}/versions")
c("tarif versiyonlar yeni ad", "GET", R + "/{R1v5}/versions")
c("tarif guncelle urunlu", "PATCH", R + "/5", {"description": "Klasik v2"}, ("R5v2", "data.id"))
c("urun tarifi tasindi", "GET", F + "/15")
c("tarif liste sonra", "GET", R)

# ---- tarif silme -----------------------------------------------------------------
c("tarif sil kosulu", "DELETE", R + "/1")
c("tarif sil urunlu", "DELETE", R + "/{R5v2}")
c("tarif sil eski versiyon", "DELETE", R + "/{R1}")
c("tarif sil", "DELETE", R + "/{R2}")
c("tarif sil olmayan", "DELETE", R + "/{R2}")

# ---- urun okuma ----------------------------------------------------------
for q in ["", "?is_active=1", "?is_active=0"]:
    c("urun liste " + (q or "varsayilan"), "GET", F + q)
# 1: siparis bekleyen ve birden cok raf; 10: alti raf; 19: depo kaydi yok
for n in [1, 9, 10, 18, 19]:
    c(f"urun detay {n}", "GET", f"{F}/{n}")
c("urun olmayan", "GET", F + "/999999")
c("urun liste depo", "GET", F, as_user="depo@olivamore.com")

# ---- urun ekleme -----------------------------------------------------------
c("urun ekle bos", "POST", F, {})
c("urun ekle hatali", "POST", F, {"name": "", "finished_product_recipe_id": 999, "volume_litre": 0, "is_active": "x",
                                  "components": [{"packaging_item_id": 999, "quantity": 0}, {"quantity": "a"}]})
c("urun ekle", "POST", F, {"name": "Test Blend 250ml", "finished_product_recipe_id": "{R3}", "volume_litre": 0.25,
                           "components": [{"packaging_item_id": 1, "quantity": 1}, {"packaging_item_id": 8, "quantity": "1"}]},
  ("P1", "data.id"))
c("urun ekle pasif", "POST", F, {"name": "Test Pasif", "finished_product_recipe_id": "{R3}", "volume_litre": "1.23456",
                                 "is_active": False, "components": [{"packaging_item_id": 4, "quantity": 2}]}, ("P2", "data.id"))
c("urun ekle null aktif", "POST", F, {"name": "Null Aktif", "finished_product_recipe_id": "{R3}", "volume_litre": 1,
                                      "is_active": None, "components": [{"packaging_item_id": 4, "quantity": 1}]}, ("P3", "data.id"))
c("urun ekle planlamaci", "POST", F, {"name": "x", "finished_product_recipe_id": 1, "volume_litre": 1,
                                      "components": [{"packaging_item_id": 4, "quantity": 1}]}, as_user=PLANNER)

# ---- urun guncelleme ---------------------------------------------------------
c("urun guncelle", "PATCH", F + "/{P1}", {"name": "Test Blend 250 ml", "volume_litre": 0.33, "is_active": "0"})
c("urun guncelle bilesen", "PUT", F + "/{P1}", {"components": [{"packaging_item_id": 2, "quantity": 3}]})
c("urun guncelle bilesen eksik", "PATCH", F + "/{P1}", {"components": [{"quantity": 3}]})
c("urun guncelle bilesen bos", "PATCH", F + "/{P1}", {"components": []})
c("urun guncelle ayni hacim", "PATCH", F + "/1", {"volume_litre": "0.25000"})
c("urun guncelle hacim stoklu", "PATCH", F + "/1", {"volume_litre": 0.5, "name": "degismez"})
c("urun guncelle hacim stoksuz", "PATCH", F + "/19", {"volume_litre": 0.55})
c("urun guncelle muhendis", "PATCH", F + "/{P2}", {"finished_product_recipe_id": 2, "is_active": True}, as_user=ENGINEER)
c("urun guncelle hatali", "PATCH", F + "/{P2}", {"name": None, "volume_litre": "abc", "finished_product_recipe_id": 999})

# ---- urun silme ------------------------------------------------------------------
c("urun sil kosulu", "DELETE", F + "/1")
c("urun sil", "DELETE", F + "/{P3}")
c("urun sil olmayan", "DELETE", F + "/{P3}")
c("tarif sil urunlu yeni", "DELETE", R + "/{R3}")

# ---- Laravel 500'leri (Java'da 422, DEVIATIONS) --------------------------------------
c("tarif ekle oran dizi", "POST", R, {"name": "Dizi", "contents": [{"production_product_type_id": 3, "ratio": [1]}]})
c("tarif ekle oran metin", "POST", R, {"name": "Metin Oran", "contents": [{"production_product_type_id": 3, "ratio": "abc"}]})
c("tarif ekle tekrar tip", "POST", R, {"name": "Tekrar", "contents": [{"production_product_type_id": 3, "ratio": 0.5},
                                                                     {"production_product_type_id": 3, "ratio": 0.5}]})
c("tarif ekle tekrar attribute", "POST", R, {"name": "Tekrar Attr", "contents": [{"production_product_type_id": 3, "ratio": 1}],
                                             "attribute_rules": [{"attribute_definition_id": 1, "value": 1},
                                                                 {"attribute_definition_id": 1, "value": 2}]})
c("urun ekle tekrar kalem", "POST", F, {"name": "Tekrar Kalem", "finished_product_recipe_id": 1, "volume_litre": 1,
                                        "components": [{"packaging_item_id": 4, "quantity": 1},
                                                       {"packaging_item_id": 4, "quantity": 1}]})
c("urun sil siparisli", "DELETE", F + "/20")

cap.write(HERE.parent / "src/test/resources/laravel/finished-product.json")
