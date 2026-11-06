"""Yakalama betiklerinin ortak parcalari (capture_<modul>.py).

Kullanim:
    cap = Capture(compose_dir, base_url)
    cap.case("ad", "POST", "/api/x", {"a": 1}, save=("X", "data.id"))
    cap.case("ad", "GET", "/api/x/{X}")            # yol ve govdede {X} -> kayitli id
    cap.sql("ad", "insert ... returning id", save="Y")
    cap.write(out_path)

Fixture bicimi Java'daki LaravelReplay ile ayni: yol/govde sablon olarak yazilir, Java kendi id'leriyle doldurur.
"""
import json
import re
import subprocess
import urllib.error
import urllib.request
from pathlib import Path


class Capture:

    def __init__(self, compose_dir, base="http://localhost:8001", email="admin@olivamore.com"):
        self.compose_dir = compose_dir
        self.base = base
        self.ids = {}
        self.cases = []
        self.token = self._call("POST", "/api/auth/login", None, {"email": email, "password": "123"})[1]["token"]

    def _call(self, method, path, token, body):
        data = json.dumps(body).encode() if body is not None else None
        r = urllib.request.Request(self.base + path, data=data, method=method)
        r.add_header("Accept", "application/json")
        if data is not None:
            r.add_header("Content-Type", "application/json")
        if token:
            r.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(r) as x:
                status, text = x.status, x.read().decode()
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode()
        try:
            return status, json.loads(text) if text else None
        except ValueError:
            return status, text[:200]

    def _path(self, text):
        return re.sub(r"\{(\w+)\}", lambda m: str(self.ids[m.group(1)]), text)

    def _fill(self, value):
        if isinstance(value, str) and re.fullmatch(r"\{\w+\}", value):
            return self.ids[value[1:-1]]
        if isinstance(value, list):
            return [self._fill(v) for v in value]
        if isinstance(value, dict):
            return {k: self._fill(v) for k, v in value.items()}
        return value

    def case(self, name, method, path, body=None, save=None):
        status, parsed = self._call(method, self._path(path), self.token, self._fill(body))
        self.cases.append({"name": name, "method": method, "path": path, "body": body, "status": status,
                           "response": parsed, "save": list(save) if save else None})
        if save and status < 300:
            node = parsed
            for part in save[1].split("."):
                node = node[int(part)] if isinstance(node, list) else node[part]
            self.ids[save[0]] = node
        print(f"{status} {name}")
        return parsed

    def sql(self, name, sql, save=None):
        out = subprocess.run(["docker", "compose", "exec", "-T", "db", "sh", "-c",
                              'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$0"', self._path(sql)],
                             cwd=self.compose_dir, capture_output=True, text=True, check=True).stdout.strip().splitlines()
        self.cases.append({"name": name, "sql": sql, "save": save})
        if save:
            self.ids[save] = int(out[0].split("|")[0])
        print(f"SQL {name}")

    def write(self, out):
        out = Path(out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"source": "Laravel " + self.base + ", dondurulmus referans veri", "cases": self.cases},
                                  ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"\n{len(self.cases)} durum -> {out}")
