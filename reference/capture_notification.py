"""Faz 10 (bildirimler) icin Laravel'in davranisini yakalar: bildirim listesi, okunmamis sayisi, okundu
isaretleme, durtme (rol ya da kisi, bekleme suresi) ve durtulecek kisiler.

Veri degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/notification.json (Java'da NotificationParityIT).

  python -I reference/capture_notification.py <laravel_compose_dir> [base_url]

Kullanicilar: admin ve muhendis (durtebilir), depo ve uretim (durtulen), operator1 (durtemez).
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
URETIM = "uretim@olivamore.com"
ENGINEER = "muhendis@olivamore.com"
OPERATOR = "operator1@olivamore.com"
N = "/api/notifications"
U = "/api/nudges"

cap.sql("depo kullanicisi", "select id from users where email = 'depo@olivamore.com'", save="DEPO")
cap.sql("admin kullanicisi", "select id from users where email = 'admin@olivamore.com'", save="ADMIN")
cap.sql("bekleyen kosu", "select id from finished_product_runs where status = 'planned' order by id limit 1", save="RUN")
cap.sql("hazir siparis", "select id from orders where status = 'ready' order by id limit 1", save="ORD")
cap.sql("onay bekleyen proses", "select id from batch_processes order by id desc limit 1", save="BP")

# ---- bos durum ------------------------------------------------------------
c("liste bos", "GET", N, as_user=DEPO)
c("okunmamis bos", "GET", N + "/unread-count", as_user=DEPO)
c("durtulecekler", "GET", U + "/targets")
c("durtulecekler muhendis", "GET", U + "/targets", as_user=ENGINEER)
c("durtulecekler yetkisiz", "GET", U + "/targets", as_user=OPERATOR)

# ---- durtme ---------------------------------------------------------------------
c("durt bos", "POST", U, {})
c("durt hatali", "POST", U, {"subject_type": "musteri", "subject_id": 0, "user_id": 999999, "note": "x" * 501})
c("durt olmayan kayit", "POST", U, {"subject_type": "order", "subject_id": 999999})
c("durt kendini", "POST", U, {"subject_type": "order", "subject_id": "{ORD}", "user_id": "{ADMIN}"})
c("durt siparis rol", "POST", U, {"subject_type": "order", "subject_id": "{ORD}", "note": "  musteri bekliyor  "})
c("durt siparis tekrar", "POST", U, {"subject_type": "order", "subject_id": "{ORD}"})
c("durt kosu kisi", "POST", U, {"subject_type": "finished_product_run", "subject_id": "{RUN}", "user_id": "{DEPO}", "note": ""},
  as_user=ENGINEER)
c("durt urun", "POST", U, {"subject_type": "finished_product", "subject_id": 1, "note": "0"})
c("durt parti", "POST", U, {"subject_type": "production_batch", "subject_id": 1}, as_user=ENGINEER)
c("durt proses admin rolu", "POST", U, {"subject_type": "batch_process", "subject_id": "{BP}"}, as_user=ENGINEER)
c("durt proses admin kendini haric", "POST", U, {"subject_type": "batch_process", "subject_id": "{BP}"})
c("durt operator", "POST", U, {"subject_type": "order", "subject_id": "{ORD}"}, as_user=OPERATOR)

# ---- alicinin bildirimleri ------------------------------------------------------
c("liste depo", "GET", N, as_user=DEPO)
c("liste depo okunmamis", "GET", N + "?unread=1", as_user=DEPO)
c("liste depo sayfali", "GET", N + "?per_page=1&page=2", as_user=DEPO)
c("liste hatali", "GET", N + "?unread=evet&per_page=101", as_user=DEPO)
c("okunmamis depo", "GET", N + "/unread-count", as_user=DEPO)
cap.sql("depo bildirimi", "select id from user_notifications where user_id = {DEPO} order by id limit 1", save="N1")
c("okundu baskasinin", "POST", N + "/{N1}/read", as_user=URETIM)
c("okundu", "POST", N + "/{N1}/read", as_user=DEPO)
c("okundu tekrar", "POST", N + "/{N1}/read", as_user=DEPO)
c("okundu olmayan", "POST", N + "/999999/read", as_user=DEPO)
c("liste depo okunmamis sonra", "GET", N + "?unread=true", as_user=DEPO)
c("hepsi okundu", "POST", N + "/read-all", as_user=DEPO)
c("hepsi okundu tekrar", "POST", N + "/read-all", as_user=DEPO)
c("okunmamis depo sonra", "GET", N + "/unread-count", as_user=DEPO)
c("liste uretim", "GET", N, as_user=URETIM)
c("liste admin", "GET", N + "?unread=0")

cap.write(HERE.parent / "src/test/resources/laravel/notification.json")
