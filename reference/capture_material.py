"""Faz 5b (materyaller) icin Laravel'in okuma cevaplarini yakalar: liste suzgecleri, detay ve soy grafi.

Veri degistirmez. Oncesinde reference/restore.sh (Java testindeki seed ile ayni veri).
Cikti: src/test/resources/laravel/material.json (Java'da MaterialParityIT).

  python -I reference/capture_material.py <laravel_compose_dir> [base_url]
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

for q in ["", "?status=consumed", "?status=all", "?status=", "?lineage=root_raw", "?lineage=root_opening",
          "?lineage=derived", "?lineage=x", "?search=%2312", "?search=gem", "?search=FILL-GEM", "?search=SIM-GEM-001",
          "?search=a%C3%A7%C4%B1l%C4%B1%C5%9F", "?status=consumed&lineage=derived&search=zeytin"]:
    c("liste " + (q or "varsayilan"), "GET", "/api/materials" + q)

# 10: dolum kosusu, depo ve siparis dugumleri; 32: baska kosu; 44: cok cocuklu; 18: devir stogu; 2335: attribute'lu
for m in [10, 32, 44, 88, 18, 2335, 1]:
    c(f"detay {m}", "GET", f"/api/materials/{m}")
    c(f"soy {m}", "GET", f"/api/materials/{m}/lineage")
c("detay olmayan", "GET", "/api/materials/999999")
c("soy olmayan", "GET", "/api/materials/999999/lineage")

cap.write(HERE.parent / "src/test/resources/laravel/material.json")
