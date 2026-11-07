"""Faz 11a (analitik ve rapor olusturucu) icin Laravel'in davranisini yakalar.

Okuma uclari veri degistirmez; kayitli raporlar degistirir. Oncesinde ve sonrasinda: reference/restore.sh
Cikti: src/test/resources/laravel/analytics.json (Java'da AnalyticsParityIT).

  python -I reference/capture_analytics.py <laravel_compose_dir> [base_url]

Aralik uzunluklari Carbon 3'un ondalik diffInDays'ini sinar: 30 gun gun gun, 31 gun haftalik; 119 gun haftalik,
120 gun aylik; 731 gunluk fark iki yilin siniri.
Kullanicilar: admin, planner (analytics.view var mi bakilir), muhendis.
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
A = "/api/analytics"

RANGES = {
    "30 gun": "from=2026-10-01&to=2026-10-30",
    "31 gun": "from=2026-10-01&to=2026-10-31",
    "119 gun": "from=2026-07-11&to=2026-11-06",
    "120 gun": "from=2026-07-10&to=2026-11-06",
    "yil": "from=2025-11-07&to=2026-11-07",
    "iki yil": "from=2024-11-08&to=2026-11-07",
    "tek gun": "from=2026-11-05&to=2026-11-05",
}

# ---- aralik dogrulamasi ---------------------------------------------------------
c("aralik yok", "GET", A + "/yield")
c("aralik hatali", "GET", A + "/yield?from=2026-1-1&to=dun")
c("aralik ters", "GET", A + "/yield?from=2026-11-07&to=2026-11-01")
c("aralik iki yildan fazla", "GET", A + "/yield?from=2024-11-07&to=2026-11-07")
c("aralik yetkisiz", "GET", A + "/yield?" + RANGES["yil"], as_user=OPERATOR)

# ---- randiman, tedarikci, musteri --------------------------------------------------
for name, q in RANGES.items():
    c("randiman " + name, "GET", A + "/yield?" + q)
c("randiman cesit", "GET", A + "/yield?" + RANGES["yil"] + "&product_type_id=1")
c("randiman tedarikci", "GET", A + "/yield?" + RANGES["yil"] + "&supplier_id=1")
c("randiman filtre hatali", "GET", A + "/yield?" + RANGES["yil"] + "&product_type_id=999&supplier_id=x")
for name in ["31 gun", "yil", "iki yil"]:
    c("tedarikciler " + name, "GET", A + "/suppliers?" + RANGES[name])
c("tedarikciler cesit", "GET", A + "/suppliers?" + RANGES["yil"] + "&product_type_id=2")
for name in ["30 gun", "120 gun", "yil"]:
    c("musteriler " + name, "GET", A + "/customers?" + RANGES[name])
for n in [1, 2, 5]:
    for name in ["31 gun", "119 gun", "yil"]:
        c(f"musteri {n} {name}", "GET", f"{A}/customers/{n}?" + RANGES[name])
c("musteri olmayan", "GET", A + "/customers/999999?" + RANGES["yil"])
c("musteri aralik yok", "GET", A + "/customers/1")

# ---- rapor olusturucu ------------------------------------------------------------
c("katalog", "GET", A + "/catalog")
METRICS = ["incoming_kg", "pressed_kg", "oil_kg", "pomace_kg", "batch_count", "yield_pct", "avg_acidity", "litres",
           "bottle_count", "order_count"]
GROUPS = ["time", "supplier", "field", "variety", "customer", "product"]
for m in METRICS:
    for g in GROUPS:
        c(f"sorgu {m} {g}", "GET", f"{A}/query?{RANGES['yil']}&metric={m}&group={g}")
c("sorgu ay ay", "GET", f"{A}/query?{RANGES['iki yil']}&metric=oil_kg&group=time&chart=line")
c("sorgu filtreli uretim", "GET", f"{A}/query?{RANGES['yil']}&metric=yield_pct&group=field&supplier_id=1&product_type_id=1")
c("sorgu filtreli satis", "GET", f"{A}/query?{RANGES['yil']}&metric=litres&group=product&customer_id=2&supplier_id=1")
c("sorgu filtre urun", "GET", f"{A}/query?{RANGES['119 gun']}&metric=bottle_count&group=time&finished_product_id=1")
c("sorgu arazi filtresi", "GET", f"{A}/query?{RANGES['yil']}&metric=incoming_kg&group=supplier&supplier_field_id=1")
c("sorgu tutarsiz gruplama", "GET", f"{A}/query?{RANGES['yil']}&metric=litres&group=supplier")
c("sorgu tutarsiz grafik", "GET", f"{A}/query?{RANGES['yil']}&metric=oil_kg&group=supplier&chart=line")
c("sorgu pasta toplanmaz", "GET", f"{A}/query?{RANGES['yil']}&metric=yield_pct&group=variety&chart=pie")
c("sorgu pasta", "GET", f"{A}/query?{RANGES['yil']}&metric=oil_kg&group=variety&chart=pie")
c("sorgu hatali", "GET", f"{A}/query?{RANGES['yil']}&metric=x&group=y&chart=z&customer_id=abc")
c("sorgu bos aralik", "GET", f"{A}/query?from=2020-01-01&to=2020-01-31&metric=oil_kg&group=supplier")

# ---- kayitli raporlar ---------------------------------------------------------------
R = A + "/reports"
CFG = {"metric": "oil_kg", "group": "time", "chart": "line", "period": {"preset": "year"}}
c("raporlar bos", "GET", R)
c("rapor ekle bos", "POST", R, {})
c("rapor ekle hatali", "POST", R, {"name": "x" * 121, "config": {"metric": "x", "period": {"from": "dun", "to": "2026-01-01"},
                                                                 "filters": {"supplier_id": "a"}}})
c("rapor ekle donemsiz", "POST", R, {"name": "Donemsiz", "config": {"metric": "oil_kg", "group": "time", "chart": "line", "period": {}}})
c("rapor ekle tutarsiz", "POST", R, {"name": "Tutarsiz", "config": {"metric": "litres", "group": "variety", "chart": "pie",
                                                                    "period": {"preset": "month"}}})
c("rapor ekle", "POST", R, {"name": "Yillik yag", "config": dict(CFG, extra="atilir", filters={"supplier_id": 1, "x": None})},
  ("R1", "data.id"))
c("rapor ekle sabit donem", "POST", R, {"name": "Ekim", "config": {"metric": "litres", "group": "customer", "chart": "bar",
                                                                   "period": {"from": "2026-10-01", "to": "2026-10-31", "preset": None}}},
  ("R2", "data.id"))
c("rapor ekle muhendis", "POST", R, {"name": "Muhendis raporu", "config": CFG}, ("R3", "data.id"), as_user=ENGINEER)
c("raporlar", "GET", R)
c("raporlar muhendis", "GET", R, as_user=ENGINEER)
c("rapor guncelle ad", "PATCH", R + "/{R1}", {"name": "Yillik yag 2"})
c("rapor guncelle ayar", "PATCH", R + "/{R2}", {"config": {"metric": "bottle_count", "group": "time", "chart": "column",
                                                            "period": {"preset": "quarter"}}})
c("rapor guncelle tutarsiz", "PATCH", R + "/{R2}", {"config": {"metric": "yield_pct", "group": "customer", "chart": "table",
                                                                "period": {"preset": "week"}}})
c("rapor guncelle baskasinin", "PATCH", R + "/{R3}", {"name": "x"})
c("rapor guncelle baskasinin hatali", "PATCH", R + "/{R3}", {"name": ""})
c("rapor guncelle olmayan", "PATCH", R + "/999999", {"name": "x"})
c("rapor sil baskasinin", "DELETE", R + "/{R3}")
c("rapor sil", "DELETE", R + "/{R2}")
c("rapor sil olmayan", "DELETE", R + "/{R2}")
c("raporlar sonra", "GET", R)

cap.write(HERE.parent / "src/test/resources/laravel/analytics.json")
