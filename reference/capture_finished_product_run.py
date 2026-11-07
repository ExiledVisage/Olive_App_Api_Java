"""Faz 8b (dolum kosulari) icin Laravel'in davranisini yakalar.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/finished-product-run.json (Java'da FinishedProductRunParityIT).

  python -I reference/capture_finished_product_run.py <laravel_compose_dir> [base_url]

Tanklar: 1 Gemlik 0.3 (2950 L, asitlik 0.30), 2 Gemlik 0.8 (0.78), 3 Ayvalik 0.4 (0.42), 5 Klasik Blend
(630 Gemlik + 270 Ayvalik), 13 bos. Urunler: 1 Gemlik 0.3 250ml, 4 Gemlik 0.3 1L, 5 Gemlik 0.3 5L,
6 Gemlik 0.8 250ml, 9 Ayvalik 0.4 250ml, 15 Klasik Blend 250ml.
Kullanicilar: admin, depo (planlama ve tamamlama), planner (yalnizca goruntuleme), muhendis (yetkisiz).
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
DEPO = "depo@olivamore.com"
PLANNER = "planner@olivamore.com"
ENGINEER = "muhendis@olivamore.com"
R = "/api/finished-product-runs"


def plan(name, product, bottles, tank, save=None, **source):
    return c(name, "POST", R, {"finished_product_id": product, "quantity": bottles,
                               "source": dict({"storage_unit_id": tank}, **source)}, save)


# ---- okuma ---------------------------------------------------------------
c("liste", "GET", R)
for q in ["?per_page=10", "?per_page=10&page=3", "?per_page=0", "?per_page=abc", "?per_page=500"]:
    c("liste " + q, "GET", R + q)
# 1: tamamlanmis; 3: devam eden; 4: planli; 14: tanki portakalli
for n in [1, 3, 4, 14, 200]:
    c(f"detay {n}", "GET", f"{R}/{n}")
c("olmayan", "GET", R + "/999999")
c("liste planlamaci", "GET", R + "?per_page=5", as_user=PLANNER)
c("liste yetkisiz", "GET", R, as_user=ENGINEER)

# ---- planlama: dogrulama ve kurallar -----------------------------------------
c("planla bos", "POST", R, {})
c("planla hatali", "POST", R, {"finished_product_id": 999, "quantity": 0, "note": 5,
                               "source": {"storage_unit_id": 999, "quantity": 0, "loss_percent": 5}})
c("planla kaynak dizi degil", "POST", R, {"finished_product_id": 1, "quantity": 1, "source": "tank"})
plan("planla bos tank", 1, 10, 13)
plan("planla yabanci urun", 1, 10, 3)
plan("planla oran uymuyor", 15, 10, 1)
plan("planla yetersiz yag", 5, 1000, 1)
plan("planla eksik kaynak", 1, 10, 1, quantity=2)
plan("planla fazla kaynak", 1, 10, 1, quantity=3)
plan("planla asitlik altinda", 6, 10, 1)
plan("planla ambalaj yetersiz", 4, 2000, 1)
c("planla planlamaci", "POST", R, {"finished_product_id": 1, "quantity": 1, "source": {"storage_unit_id": 1}},
  as_user=PLANNER)

# ---- planlama: basarili ----------------------------------------------------------
plan("planla", 1, 20, 1, ("R1", "data.id"))
c("planla not ve metin adet", "POST", R, {"finished_product_id": 1, "quantity": "12", "note": "aksam vardiyasi",
                                          "source": {"storage_unit_id": "1", "loss_percent": 1.5}}, ("R2", "data.id"))
plan("planla kaynak miktarli", 1, 10, 1, ("R3", "data.id"), quantity=2.55)
plan("planla blend", 15, 40, 5, ("R4", "data.id"))
plan("planla ayvalik", 9, 8, 3, ("R5", "data.id"), loss_percent="2")
plan("planla depo", 6, 16, 2, ("R6", "data.id"), as_user=DEPO)
c("detay yeni", "GET", R + "/{R1}")

# ---- ambalaj tahsisi -----------------------------------------------------------
P = R + "/{R3}/packagings"
c("tahsis bos", "PUT", P, {})
c("tahsis hatali", "PUT", P, {"packagings": [{"packaging_lot_id": 999999, "quantity": 0}, {"packaging_lot_id": "x"}]})
c("tahsis tekrar lot", "PUT", P, {"packagings": [{"packaging_lot_id": 330, "quantity": 5}, {"packaging_lot_id": 330, "quantity": 5}]})
c("tahsis yabanci kalem", "PUT", P, {"packagings": [{"packaging_lot_id": 16, "quantity": 10}]})
c("tahsis lotta yetersiz", "PUT", P, {"packagings": [{"packaging_lot_id": 3, "quantity": 5}]})
c("tahsis eksik kalem", "PUT", P, {"packagings": [{"packaging_lot_id": 330, "quantity": 10}]})
c("tahsis adet uymuyor", "PUT", P, {"packagings": [{"packaging_lot_id": 330, "quantity": 9}, {"packaging_lot_id": 335, "quantity": 10},
                                                   {"packaging_lot_id": 332, "quantity": 10}, {"packaging_lot_id": 334, "quantity": 10}]})
c("tahsis", "PUT", P, {"packagings": [{"packaging_lot_id": 3, "quantity": 2}, {"packaging_lot_id": 330, "quantity": "8"},
                                      {"packaging_lot_id": 24, "quantity": 3}, {"packaging_lot_id": 335, "quantity": 7},
                                      {"packaging_lot_id": 332, "quantity": 10}, {"packaging_lot_id": 334, "quantity": 10}]})
c("tahsis planlamaci", "PUT", P, {"packagings": [{"packaging_lot_id": 330, "quantity": 10}]}, as_user=PLANNER)

# ---- attribute degeri --------------------------------------------------------------
cap.sql("deger R1", "select id from finished_product_run_attribute_values where finished_product_run_id = {R1}", save="V1")
cap.sql("deger eski", "select id from finished_product_run_attribute_values where finished_product_run_id = 1 limit 1", save="V0")
V = R + "/{R1}/attribute-values/"
c("deger guncelle", "PATCH", V + "{V1}", {"actual_value": 0.31})
c("deger guncelle ayni", "PATCH", V + "{V1}", {"actual_value": "0.31000"})
c("deger guncelle hatali", "PATCH", V + "{V1}", {"actual_value": "abc"})
c("deger baska kosunun", "PATCH", V + "{V0}", {"actual_value": 1})
c("deger baska kosunun hatali", "PATCH", V + "{V0}", {})
c("deger olmayan", "PATCH", V + "999999", {"actual_value": 1})
c("deger planlamaci", "PATCH", V + "{V1}", {"actual_value": 1}, as_user=PLANNER)

# ---- durum gecisleri ----------------------------------------------------------------
S = R + "/{R1}/status"
c("durum hatali", "PATCH", S, {"status": "bogus"})
c("durum atlama", "PATCH", S, {"status": "completed"})
c("durum ayni", "PATCH", S, {"status": "planned"})
c("durum baslat", "PATCH", S, {"status": "in_progress"})
c("durum baslat tekrar", "PATCH", S, {"status": "in_progress"})
c("durum tamamla", "PATCH", S, {"status": "completed"})
c("durum tamamlanmis iptal", "PATCH", S, {"status": "cancelled"})
c("detay tamamlanan", "GET", R + "/{R1}")
c("tank 1 tamamlamadan sonra", "GET", "/api/storage-units/1")
c("kalem 1 tamamlamadan sonra", "GET", "/api/packaging-items/1")
c("lot 330 tamamlamadan sonra", "GET", "/api/packaging-lots/330")
c("urun 1 tamamlamadan sonra", "GET", "/api/finished-products/1")
c("tahsis tamamlanmis", "PUT", R + "/{R1}/packagings", {"packagings": [{"packaging_lot_id": 330, "quantity": 20}]})

# elle tahsisli kosu (lot 3'un son 2 sisesi dahil)
c("elle tahsisli baslat", "PATCH", R + "/{R3}/status", {"status": "in_progress"})
c("elle tahsisli tamamla", "PATCH", R + "/{R3}/status", {"status": "completed"}, as_user=DEPO)
c("lot 3 tukendi", "GET", "/api/packaging-lots/3")

# tahsisten sonra lot bosaldi
cap.sql("R2 lotu bosalt", "update packaging_lots set quantity_remaining = 1 where id = (select packaging_lot_id from"
        " finished_product_run_packagings where finished_product_run_id = {R2} order by id limit 1) returning id", save="L2")
c("tahsis bozuk baslat", "PATCH", R + "/{R2}/status", {"status": "in_progress"})
c("tahsis bozuk tamamla", "PATCH", R + "/{R2}/status", {"status": "completed"})
cap.sql("R2 lotu geri", "update packaging_lots set quantity_remaining = 1332 where id = {L2} returning id")

# tanktaki materyal azaldi
cap.sql("R5 materyali azalt", "update materials set quantity = 1 where id = 3 returning id")
c("tank degisti baslat", "PATCH", R + "/{R5}/status", {"status": "in_progress"})
c("tank degisti tamamla", "PATCH", R + "/{R5}/status", {"status": "completed"})
cap.sql("R5 materyali tuket", "update materials set status = 'consumed' where id = 3 returning id")
c("tank degisti tuketilmis", "PATCH", R + "/{R5}/status", {"status": "completed"})
cap.sql("R5 materyali geri", "update materials set quantity = 1950, status = 'active' where id = 3 returning id")

# blend (iki materyal) ve seed'deki devam eden kosu
c("blend baslat", "PATCH", R + "/{R4}/status", {"status": "in_progress"})
c("blend tamamla", "PATCH", R + "/{R4}/status", {"status": "completed"})
c("tank 5 blend sonrasi", "GET", "/api/storage-units/5")
c("seed kosu tamamla", "PATCH", R + "/3/status", {"status": "completed"})
c("seed planli baslat", "PATCH", R + "/4/status", {"status": "in_progress"}, as_user=DEPO)
c("durum planlamaci", "PATCH", R + "/17/status", {"status": "in_progress"}, as_user=PLANNER)

# ---- iptal ve silme --------------------------------------------------------------------
c("iptal", "PATCH", R + "/{R6}/status", {"status": "cancelled"})
c("iptal edilen tahsis", "PUT", R + "/{R6}/packagings", {"packagings": [{"packaging_lot_id": 330, "quantity": 16}]})
c("sil iptal edilmis", "DELETE", R + "/{R6}")
c("sil tamamlanmis", "DELETE", R + "/{R1}")
c("sil planli", "DELETE", R + "/17")
c("sil olmayan", "DELETE", R + "/17")
c("sil planlamaci", "DELETE", R + "/12", as_user=PLANNER)
c("liste sonra", "GET", R + "?per_page=8")

cap.write(HERE.parent / "src/test/resources/laravel/finished-product-run.json")
