#!/usr/bin/env python3
"""0.21-07 round-trip: export -> preview -> apply на живом API.

Доказывает, что выгруженный workbook принимается обратно без ошибок
и повторное применение не плодит дубликаты (create=0 на втором заходе).
"""
import json
import sys
import urllib.request
import uuid

API = "http://localhost:8082/api/v1"
USER = "obuhov"
PASSWORD = sys.argv[1] if len(sys.argv) > 1 else ""


def req(path, token=None, data=None, method=None, ctype=None, raw=False):
    r = urllib.request.Request(API + path, data=data,
                               method=method or ("POST" if data else "GET"))
    if ctype:
        r.add_header("Content-Type", ctype)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r) as resp:
        body = resp.read()
        if raw:
            return resp.status, body
        text = body.decode()
        return resp.status, (json.loads(text) if text.strip() else {})


st, res = req("/auth/login",
              data=json.dumps({"username": USER, "password": PASSWORD}).encode(),
              ctype="application/json")
token = res.get("token") or res.get("accessToken")
print("login:", st, bool(token))

st, book = req("/data-sync/export?format=xlsx&version=0.21", token=token, raw=True)
print("export:", st, len(book), "bytes")

boundary = uuid.uuid4().hex
body = b"".join([
    f"--{boundary}\r\n".encode(),
    b'Content-Disposition: form-data; name="file"; filename="wolf.xlsx"\r\n',
    b"Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n",
    book, b"\r\n", f"--{boundary}--\r\n".encode(),
])
st, prev = req("/data-sync/import/preview", token=token, data=body,
               ctype=f"multipart/form-data; boundary={boundary}")
print("preview:", st, "id:", prev.get("id"), "status:", prev.get("status"),
      "applyAllowed:", prev.get("applyAllowed"), "errors:", len(prev.get("errors") or []))
plan = prev.get("plan") or {}
tot = lambda k: sum(v.get(k, 0) for v in plan.values())
print("plan totals: create=%d update=%d skip=%d delete=%d"
      % (tot("create"), tot("update"), tot("skip"), tot("delete")))

if not prev.get("applyAllowed"):
    print("errors:", json.dumps((prev.get("errors") or [])[:5], ensure_ascii=False))
    print("RESULT: FAIL (preview не допускает применение)")
    sys.exit(1)

st, applied = req(f"/data-sync/import/{prev['id']}/apply", token=token,
                  data=json.dumps({"checksum": prev["checksum"],
                                   "deleteMissing": False, "scopes": []}).encode(),
                  ctype="application/json")
s = lambda m: sum((m or {}).values())
print("apply:", st, "status:", applied.get("status"),
      "created:", s(applied.get("created")),
      "updated:", s(applied.get("updated")),
      "deleted:", s(applied.get("deleted")))

ok = st == 200 and s(applied.get("created")) == 0 and s(applied.get("deleted")) == 0
print("RESULT:", "PASS" if ok else "FAIL",
      "— round-trip идемпотентен" if ok else "— round-trip изменил данные")
sys.exit(0 if ok else 1)
