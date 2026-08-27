#!/usr/bin/env python3
"""Браузерная приёмка тикета 0.21-06 — UI синхронизации данных.

Гейт WOLF: аутентификация в реальном браузере + инспекция живого DOM.
Проверяет то, что нельзя доказать grep'ом по бандлу:
экспорт, отсутствие авто-применения, состояние Apply, видимость
destructive-опций и вывод manifest/expiry.
"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp_driver import connect, login, loaded_bundles  # noqa: E402

BASE = os.environ.get("WOLF_BASE", "http://localhost")
USER = os.environ.get("WOLF_USER", "obuhov")
PASSWORD = os.environ.get("WOLF_PASSWORD", "")


async def main():
    c = await connect()
    await c.set_viewport(1440, 900)

    if not await login(c, BASE, USER, PASSWORD):
        print("FAIL: не удалось войти")
        return 1
    print("login: True")

    # Роутер в hash-режиме (createWebHashHistory) — путь без # даёт пустой шелл.
    await c.goto(f"{BASE}/#/data-sync", wait_ms=2500)
    print("bundles:", await loaded_bundles(c))

    state = await c.evaluate("""
    (() => {
      const q = (s) => document.querySelector(s);
      const txt = (s) => { const e = q(s); return e ? e.textContent.trim() : null; };
      const btns = [...document.querySelectorAll('.data-sync-page button')]
        .map(b => ({text: b.textContent.trim(), disabled: b.disabled}));
      const fileInput = q('[data-testid="sync-file"]');
      return {
        heading: txt('.data-sync-page h1'),
        buttons: btns,
        fileInput: fileInput ? {
          type: fileInput.type,
          accept: fileInput.accept,
          visible: fileInput.getBoundingClientRect().width > 0,
          labelled: !!fileInput.closest('label'),
        } : null,
        manifest: txt('[data-testid="sync-manifest"]'),
        previewShown: !!q('.preview'),
        deleteOptionsShown: !!q('.delete-options'),
        resultShown: !!q('[data-testid="sync-result"]'),
      };
    })()
    """)
    print("state:", state)

    checks = {
        "заголовок раздела": state.get("heading") == "Синхронизация данных",
        "file input .xlsx + подпись": bool(
            state.get("fileInput")
            and state["fileInput"]["type"] == "file"
            and ".xlsx" in (state["fileInput"]["accept"] or "")
            and state["fileInput"]["labelled"]
        ),
        "manifest/version отображён": bool(state.get("manifest") and "0.21" in state["manifest"]),
        "загрузка не применяется автоматически": not state.get("resultShown"),
        "preview не показан до запуска": not state.get("previewShown"),
        "destructive-опции скрыты до preview": not state.get("deleteOptionsShown"),
    }

    # Кнопка Apply отсутствует, пока нет валидного preview; preview-кнопка
    # заблокирована, пока не выбран файл.
    preview_btn = next((b for b in state["buttons"] if "preview" in b["text"].lower()), None)
    apply_btn = next((b for b in state["buttons"] if "Примен" in b["text"]), None)
    checks["preview заблокирован без файла"] = bool(preview_btn and preview_btn["disabled"])
    checks["Apply недоступен без preview"] = apply_btn is None or apply_btn["disabled"]
    checks["экспорт доступен"] = any(
        "Скачать" in b["text"] and not b["disabled"] for b in state["buttons"]
    )

    for name, ok in checks.items():
        print(f"  {'OK  ' if ok else 'FAIL'} {name}")

    passed = all(checks.values())
    print("RESULT:", "PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
