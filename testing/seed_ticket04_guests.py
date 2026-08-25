#!/usr/bin/env python3
"""Готовит гостей для браузерной проверки тикета 04 на стеке worktree (api :8086)."""
import json
import urllib.request
import urllib.error

API = "http://localhost:8086/api/v1"


def call(path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method="POST" if data is not None else "GET")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


st, admin = call("/auth/login", {"username": "admin", "password": "admin"})
print("admin login", st)
token = admin["token"]

for user in ("keeper", "purger"):
    st, inv = call("/auth/invite", {"maxUses": 1}, token)
    print("invite", st, inv)
    st, reg = call("/auth/register", {"code": inv["code"], "username": user, "password": "password123"})
    print("register", user, st)
    st, res = call("/onboarding/load-profile", {"slug": "wise-freelancer"}, reg["token"])
    print("load-profile", user, st, res)
    print(user, "token=", reg["token"])
