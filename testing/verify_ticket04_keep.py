#!/usr/bin/env python3
"""Браузерная проверка тикета 04 — ветка «Оставить предзаполненный профиль».

Гейт WOLF: аутентификация в браузере + инспекция реального DOM, не grep по сборке.
"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.expanduser("~/.hermes/skills/wolf-life-os/scripts"))
import cdp_driver

cdp_driver.CDP_HTTP = "http://127.0.0.1:9336"
from cdp_driver import connect, login, loaded_bundles  # noqa: E402

BASE = "http://localhost:5177"


async def main():
    c = await connect()
    await c.set_viewport(1440, 900)

    ok = await login(c, BASE, "keeper", "password123")
    print("login keeper:", ok)

    await c.goto(f"{BASE}/#/onboarding/final")
    await asyncio.sleep(1.5)

    print("загруженные бандлы:", await loaded_bundles(c))
    print("hash:", await c.eval("location.hash"))
    print("заголовок:", await c.eval("document.querySelector('#final-choice-title')?.textContent"))

    opts = await c.eval("""(() => [...document.querySelectorAll('[data-final-choice]')]
        .map(b => ({k: b.dataset.finalChoice, t: b.querySelector('.choice-name')?.textContent.trim()})))()""")
    print("кнопки выбора:", opts)

    # тихий контракт 0.3: без прогресс-бара, без цветных индикаторов
    quiet = await c.eval("""(() => {
      const btn = document.querySelector('[data-final-choice]');
      const cs = getComputedStyle(btn);
      return {
        progressBars: document.querySelectorAll('progress,[role=progressbar]').length,
        svgIcons: document.querySelectorAll('svg').length,
        stepCounter: /шаг\\s*\\d+\\s*из\\s*\\d+/i.test(document.body.innerText),
        boxShadow: cs.boxShadow, borderRadius: cs.borderRadius,
      };
    })()""")
    print("тихий контракт:", quiet)

    # подтверждающего второго диалога быть не должно — клик сразу выполняет ветку
    box = await c.eval("""(() => {
      const b = document.querySelector('[data-final-choice=keep]').getBoundingClientRect();
      return {x: b.left + b.width/2, y: b.top + 24};
    })()""")
    await c.click_xy(box["x"], box["y"])
    await asyncio.sleep(2.5)

    print("после клика hash:", await c.eval("location.hash"))
    print("диалогов подтверждения:", await c.eval(
        "document.querySelectorAll('dialog[open],[role=alertdialog]').length"))

    # данные профиля видны после редиректа на /calendar
    await asyncio.sleep(2.0)
    seen = await c.eval("""(() => {
      const t = document.body.innerText;
      return {hash: location.hash, len: t.length,
              hasEntries: document.querySelectorAll('[class*=entry],[class*=slot],[class*=cell]').length};
    })()""")
    print("Ежедневник:", seen)

    status = await c.eval("""(async () => {
      const r = await fetch('/api/v1/onboarding/status',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      return await r.json();
    })()""", awaitp=True)
    print("onboarding status:", status)

    projects = await c.eval("""(async () => {
      const r = await fetch('/api/v1/projects',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      return Array.isArray(d) ? d.length : (d.content ? d.content.length : JSON.stringify(d).slice(0,120));
    })()""", awaitp=True)
    print("Проектов у keeper после «Оставить»:", projects)

    await c.screenshot("/tmp/ticket04-keep.png")
    print("скриншот: /tmp/ticket04-keep.png")


asyncio.run(main())
