#!/usr/bin/env python3
"""Браузерная проверка тикета 0.6-08 — версия WOLF в шапке.

Гейт WOLF: аутентификация в браузере + инспекция живого DOM.
"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cdp_driver  # noqa: E402
from cdp_driver import connect, login, loaded_bundles  # noqa: E402

BASE = os.environ.get("WOLF_BASE", "http://localhost")
USER = os.environ.get("WOLF_USER", "obuhov")
PASSWORD = os.environ.get("WOLF_PASSWORD", "")


async def main():
    c = await connect()
    await c.set_viewport(1440, 900)

    ok = await login(c, BASE, USER, PASSWORD)
    print("login:", ok)
    if not ok:
        print("FAIL: не удалось войти")
        return 1

    # Десктопная шапка
    await c.goto(f"{BASE}/morning", wait_ms=2500)
    bundles = await loaded_bundles(c)
    print("bundles:", bundles)

    desktop = await c.evaluate("""
    (() => {
      const el = document.querySelector('.brand-container .brand-version');
      if (!el) return {found: false};
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        found: true,
        text: el.textContent.trim(),
        color: cs.color,
        fontSize: cs.fontSize,
        background: cs.backgroundColor,
        visible: r.width > 0 && r.height > 0,
      };
    })()
    """)
    print("desktop .brand-version:", desktop)

    tagline = await c.evaluate("""
    (() => {
      const el = document.querySelector('.brand-container .brand-tagline');
      return el ? getComputedStyle(el).color : null;
    })()
    """)
    print("tagline color (для сверки тихого контракта):", tagline)

    # Мобильная ширина + открытый drawer
    await c.set_viewport(420, 820)
    await c.goto(f"{BASE}/morning", wait_ms=2000)
    mobile = await c.evaluate("""
    (async () => {
      const btn = document.querySelector('.menu-trigger');
      if (!btn) return {found: false, reason: 'no .menu-trigger'};
      btn.click();
      await new Promise(r => setTimeout(r, 600));
      const el = document.querySelector('.brand-container-sm .brand-version-sm');
      if (!el) return {found: false, reason: 'no .brand-version-sm'};
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        found: true,
        text: el.textContent.trim(),
        color: cs.color,
        fontSize: cs.fontSize,
        background: cs.backgroundColor,
        visible: r.width > 0 && r.height > 0,
      };
    })()
    """)
    print("mobile .brand-version-sm:", mobile)

    passed = (
        desktop and desktop.get("found") and desktop.get("visible")
        and desktop.get("text", "").startswith("v")
        and mobile and mobile.get("found") and mobile.get("visible")
        and mobile.get("text", "").startswith("v")
    )
    print("RESULT:", "PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
