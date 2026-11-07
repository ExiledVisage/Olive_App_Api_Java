"""Faz 11b (gunluk ozet) icin Laravel'in cevaplarini yakalar: her rol icin (bolumler izne gore), dolu gunler,
saatli tarih, hatali tarih.

Bir durum onay bekleyen asama icin veri degistirir (SQL). Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/daily-summary.json (Java'da DailySummaryParityIT).

  python -I reference/capture_daily_summary.py <laravel_compose_dir> [base_url]
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
D = "/api/daily-summary"
USERS = ["operator1", "operator2", "planner", "uretim", "depo", "muhendis"]

# 2026-11-04: uc parti girdi; 2026-11-06: 11 dolum bitti; 2026-09-29: dort siparis sevk edildi
DATES = ["2026-11-04", "2026-11-06", "2026-09-29", "2026-01-29", "2020-01-01"]

for d in DATES:
    c("admin " + d, "GET", f"{D}?date={d}")
for u in USERS:
    c(u, "GET", f"{D}?date=2026-11-06", as_user=f"{u}@olivamore.com")
c("saatli tarih", "GET", D + "?date=2026-11-06%2023:30")
c("iso tarih", "GET", D + "?date=2026-11-04T08:00:00%2B03:00")
c("hatali tarih", "GET", D + "?date=dun")
c("bos tarih", "GET", D + "?date=")

cap.sql("onay bekleyen", "with u as (update batch_processes set status = 'awaiting_approval', submitted_at = '2026-11-06 10:00:00'"
        " where id in (select id from batch_processes where status = 'completed' order by id desc limit 2) returning id)"
        " select count(*) from u")
c("onay bekleyenli", "GET", D + "?date=2026-11-06")

cap.write(HERE.parent / "src/test/resources/laravel/daily-summary.json")
