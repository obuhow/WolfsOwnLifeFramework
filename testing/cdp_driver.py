#!/usr/bin/env python3
"""Минимальный CDP-драйвер для браузерной приёмки WOLF.

Гейт WOLF: аутентификация в реальном браузере + инспекция живого DOM,
а не grep по собранному бандлу.

Требует запущенный Chrome с --remote-debugging-port (по умолчанию 9336).
Зависимости: только websockets (или fallback на стандартный socket-хендшейк).
"""
import asyncio
import json
import urllib.request

CDP_HTTP = "http://127.0.0.1:9336"


class Client:
    def __init__(self, ws):
        self.ws = ws
        self._id = 0

    async def send(self, method, **params):
        self._id += 1
        msg = {"id": self._id, "method": method, "params": params}
        await self.ws.send(json.dumps(msg))
        while True:
            raw = await self.ws.recv()
            data = json.loads(raw)
            if data.get("id") == self._id:
                if "error" in data:
                    raise RuntimeError(f"{method}: {data['error']}")
                return data.get("result", {})

    async def goto(self, url, wait_ms=1500):
        await self.send("Page.navigate", url=url)
        await asyncio.sleep(wait_ms / 1000)

    async def evaluate(self, expr):
        r = await self.send(
            "Runtime.evaluate",
            expression=expr,
            returnByValue=True,
            awaitPromise=True,
        )
        res = r.get("result", {})
        if res.get("subtype") == "error":
            raise RuntimeError(res.get("description"))
        return res.get("value")

    async def set_viewport(self, w, h):
        await self.send(
            "Emulation.setDeviceMetricsOverride",
            width=w, height=h, deviceScaleFactor=1, mobile=False,
        )

    async def clear_storage(self, origin):
        try:
            await self.send("Storage.clearDataForOrigin",
                            origin=origin, storageTypes="all")
        except Exception:
            pass


async def connect(port=None):
    import websockets
    base = CDP_HTTP if port is None else f"http://127.0.0.1:{port}"
    # Открываем новую вкладку, чтобы не мешать другим проверкам.
    with urllib.request.urlopen(f"{base}/json/new?about:blank", data=b"") as r:
        tab = json.loads(r.read())
    ws = await websockets.connect(
        tab["webSocketDebuggerUrl"], max_size=50 * 1024 * 1024
    )
    c = Client(ws)
    await c.send("Page.enable")
    await c.send("Runtime.enable")
    return c


async def login(c, base, username, password):
    """Логин через реальный API + запись токена в localStorage приложения."""
    await c.goto(f"{base}/login")
    expr = f"""
    (async () => {{
      const r = await fetch('/api/v1/auth/login', {{
        method: 'POST',
        headers: {{'Content-Type': 'application/json'}},
        body: JSON.stringify({{username: {json.dumps(username)},
                              password: {json.dumps(password)}}})
      }});
      if (!r.ok) return {{ok: false, status: r.status}};
      const d = await r.json();
      localStorage.setItem('wolf_token', d.token);
      return {{ok: true, status: r.status}};
    }})()
    """
    res = await c.evaluate(expr)
    return bool(res and res.get("ok"))


async def loaded_bundles(c):
    """Какие ассеты реально отдал сервер — сверка с web/dist."""
    return await c.evaluate("""
    Array.from(document.querySelectorAll('script[src],link[href]'))
      .map(e => e.src || e.href)
      .filter(u => u.includes('/assets/'))
    """)
