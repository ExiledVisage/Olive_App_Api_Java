"""Faz 9b (siparisler) icin Laravel'in davranisini yakalar: siparis CRUD, durum gecisleri, stok kontrolu,
hazirlamada raftan alma (FIFO ve elle), sevkiyat, hazir siparis iptalinde rafa geri koyma.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/order.json (Java'da OrderParityIT).

  python -I reference/capture_order.py <laravel_compose_dir> [base_url]

Raflarda urun 1 (raf 14: 12, raf 16: 148), urun 9 (raf 3: 140), urun 2 (raf 11, 17, 18); urun 19'un stogu yok.
Kullanicilar: admin, planner (siparis yonetimi), depo (durum), operator1 (yalnizca goruntuleme), muhendis (yetkisiz).
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
DEPO = "depo@olivamore.com"
OPERATOR = "operator1@olivamore.com"
ENGINEER = "muhendis@olivamore.com"
O = "/api/orders"

for status in ["draft", "confirmed", "stock_check", "in_production", "ready", "shipped", "cancelled"]:
    cap.sql("seed " + status, f"select id from orders where status = '{status}' order by id limit 1", save="S_" + status.upper())
cap.sql("musteri 1 adresi", "select id from customer_addresses where customer_id = 1 order by id limit 1", save="A1")
cap.sql("musteri 2 adresi", "select id from customer_addresses where customer_id = 2 order by id limit 1", save="A2")

# ---- okuma ---------------------------------------------------------------
for q in ["", "?status=ready", "?status=stock_check", "?customer_id=2", "?customer_id=2&status=shipped", "?per_page=10&page=2",
          "?status=", "?per_page=0"]:
    c("liste " + (q or "varsayilan"), "GET", O + q)
for s in ["DRAFT", "CONFIRMED", "STOCK_CHECK", "READY", "SHIPPED", "CANCELLED"]:
    c("detay " + s.lower(), "GET", O + "/{S_%s}" % s)
    c("stok kontrol " + s.lower(), "GET", O + "/{S_%s}/stock-check" % s)
c("olmayan", "GET", O + "/999999")
c("stok kontrol olmayan", "GET", O + "/999999/stock-check")
c("liste operator", "GET", O + "?per_page=3", as_user=OPERATOR)
c("liste yetkisiz", "GET", O, as_user=ENGINEER)

# ---- olusturma ---------------------------------------------------------------
c("ekle bos", "POST", O, {})
c("ekle hatali", "POST", O, {"customer_id": 999, "delivery_address_id": "x", "order_date": "dun", "note": 5,
                             "items": [{"finished_product_id": 999, "quantity": 0}, {}]})
c("ekle tekrar urun", "POST", O, {"customer_id": 1, "delivery_address_id": "{A1}", "order_date": "2026-11-07",
                                  "items": [{"finished_product_id": 1, "quantity": 1}, {"finished_product_id": 1, "quantity": 2}]})
c("ekle baska musterinin adresi", "POST", O, {"customer_id": 1, "delivery_address_id": "{A2}", "order_date": "2026-11-07",
                                              "items": [{"finished_product_id": 1, "quantity": 1}]})
c("ekle", "POST", O, {"customer_id": 1, "delivery_address_id": "{A1}", "order_date": "2026-11-07 15:30", "note": "acil",
                      "items": [{"finished_product_id": 1, "quantity": 5}, {"finished_product_id": 9, "quantity": "3"}]}, ("O1", "data.id"))
c("ekle ikinci", "POST", O, {"customer_id": 2, "delivery_address_id": "{A2}", "order_date": "2026-11-06T22:00:00+03:00",
                             "items": [{"finished_product_id": 1, "quantity": 20}, {"finished_product_id": 2, "quantity": 10}]},
  ("O2", "data.id"), as_user=PLANNER)
c("ekle stoksuz", "POST", O, {"customer_id": 2, "delivery_address_id": "{A2}", "order_date": "2026-11-07",
                              "items": [{"finished_product_id": 19, "quantity": 1}]}, ("O3", "data.id"))
c("ekle silinecek", "POST", O, {"customer_id": 1, "delivery_address_id": "{A1}", "order_date": "2026-11-07",
                                "items": [{"finished_product_id": 3, "quantity": 1}]}, ("O4", "data.id"))
c("ekle operator", "POST", O, {"customer_id": 1, "delivery_address_id": "{A1}", "order_date": "2026-11-07",
                               "items": [{"finished_product_id": 3, "quantity": 1}]}, as_user=OPERATOR)

# ---- guncelleme ------------------------------------------------------------------
c("guncelle", "PATCH", O + "/{O1}", {"note": None, "order_date": "2026-11-08"})
c("guncelle kalemler", "PATCH", O + "/{O1}", {"items": [{"finished_product_id": 1, "quantity": 4}, {"finished_product_id": 9, "quantity": 2}]})
c("guncelle musteri adres uyusmaz", "PATCH", O + "/{O1}", {"customer_id": 2})
c("guncelle hatali", "PATCH", O + "/{O1}", {"order_date": "x", "items": [{"quantity": 1}]})
c("guncelle sevk edilmis", "PATCH", O + "/{S_SHIPPED}", {"note": "x"})
c("guncelle bos", "PATCH", O + "/{O1}", {})

# ---- durum: otomatik raf secimi ve iptal ------------------------------------------------
S = O + "/{O1}/status"
c("durum hatali", "PATCH", S, {"status": "draft", "note": "x" * 1001})
c("durum atlama", "PATCH", S, {"status": "ready"})
c("durum onay", "PATCH", S, {"status": "confirmed", "note": "telefonla onaylandi"})
c("durum stok kontrol", "PATCH", S, {"status": "stock_check"}, as_user=DEPO)
c("stok kontrol yeni", "GET", O + "/{O1}/stock-check")
c("durum hazir otomatik", "PATCH", S, {"status": "ready", "picks": []})
c("urun 1 hazirlamadan sonra", "GET", "/api/finished-products/1")
c("depo 3 hareketleri", "GET", "/api/finished-product-storages/3/movements")
c("guncelle hazir", "PATCH", O + "/{O1}", {"note": "x"})
c("durum hazir iptal", "PATCH", S, {"status": "cancelled", "note": "musteri vazgecti"})
c("detay iptal", "GET", O + "/{O1}")
c("depo 3 iptalden sonra", "GET", "/api/finished-product-storages/3")
c("durum iptal edilmis", "PATCH", S, {"status": "confirmed"})

# ---- durum: elle raf secimi ve sevkiyat -------------------------------------------------
S2 = O + "/{O2}/status"
cap.sql("O2 kalem 1", "select id from order_items where order_id = {O2} order by id limit 1", save="I1")
cap.sql("O2 kalem 2", "select id from order_items where order_id = {O2} order by id offset 1 limit 1", save="I2")
cap.sql("baska kalem", "select id from order_items where order_id = {S_SHIPPED} order by id limit 1", save="IX")
c("O2 onay", "PATCH", S2, {"status": "confirmed"})
c("O2 stok kontrol", "PATCH", S2, {"status": "stock_check"})
c("O2 hazir baska kalem", "PATCH", S2, {"status": "ready", "picks": [{"order_item_id": "{IX}", "slot_id": 14, "quantity": 1}]})
c("O2 hazir adet eksik", "PATCH", S2, {"status": "ready", "picks": [{"order_item_id": "{I1}", "slot_id": 14, "quantity": 12},
                                                                     {"order_item_id": "{I2}", "slot_id": 11, "quantity": 10}]})
c("O2 hazir rafta yok", "PATCH", S2, {"status": "ready", "picks": [{"order_item_id": "{I1}", "slot_id": 3, "quantity": 20},
                                                                    {"order_item_id": "{I2}", "slot_id": 11, "quantity": 10}]})
c("O2 hazir secim hatali", "PATCH", S2, {"status": "ready", "picks": [{"order_item_id": 999999, "slot_id": "x", "quantity": 0}]})
c("O2 hazir elle", "PATCH", S2, {"status": "ready", "picks": [{"order_item_id": "{I1}", "slot_id": 14, "quantity": 12},
                                                              {"order_item_id": "{I1}", "slot_id": 16, "quantity": 8},
                                                              {"order_item_id": "{I2}", "slot_id": 11, "quantity": 10}]}, as_user=DEPO)
c("stok kontrol hazir", "GET", O + "/{O2}/stock-check")
c("O2 sevk", "PATCH", S2, {"status": "shipped"})
c("urun 2 sevkten sonra", "GET", "/api/finished-products/2")
c("O2 sevk sonrasi iptal", "PATCH", S2, {"status": "cancelled"})
c("O2 detay", "GET", O + "/{O2}")

# ---- durum: stok yetersiz, uretimde ------------------------------------------------------
S3 = O + "/{O3}/status"
c("O3 onay", "PATCH", S3, {"status": "confirmed"})
c("O3 stok kontrol", "PATCH", S3, {"status": "stock_check"})
c("O3 stok kontrol verisi", "GET", O + "/{O3}/stock-check")
c("O3 hazir rafta yok", "PATCH", S3, {"status": "ready"})
c("O3 uretimde", "PATCH", S3, {"status": "in_production"})
c("O3 uretimde iptal", "PATCH", S3, {"status": "cancelled"})
c("durum operator", "PATCH", O + "/{S_CONFIRMED}/status", {"status": "stock_check"}, as_user=OPERATOR)

# ---- seed siparisleri ------------------------------------------------------------------
c("seed hazir sevk", "PATCH", O + "/{S_READY}/status", {"status": "shipped"}, as_user=DEPO)
c("seed stok kontrol hazir", "PATCH", O + "/{S_STOCK_CHECK}/status", {"status": "ready"})
c("seed uretimde hazir", "PATCH", O + "/{S_IN_PRODUCTION}/status", {"status": "ready"})

# ---- silme -----------------------------------------------------------------------------
c("sil onayli", "DELETE", O + "/{S_CONFIRMED}")
c("sil taslak", "DELETE", O + "/{O4}")
c("sil olmayan", "DELETE", O + "/{O4}")
c("sil seed taslak planlamaci", "DELETE", O + "/{S_DRAFT}", as_user=PLANNER)
c("liste sonra", "GET", O + "?per_page=6")

cap.write(HERE.parent / "src/test/resources/laravel/order.json")
