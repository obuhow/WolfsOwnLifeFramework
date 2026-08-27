#!/usr/bin/env python3
"""Браузерная проверка тикета 04 — ветка «Очистить профиль».

Проверяет: экраны Проектов/Дел/Целей пусты, мастер 0.4-08 запускается,
после его завершения «Сегодня» показывает остаток нормы по введённым данным.
"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.expanduser("~/.hermes/skills/wolf-life-os/scripts"))
import cdp_driver

cdp_driver.CDP_HTTP = "http://127.0.0.1:9336"
from cdp_driver import connect, login, loaded_bundles  # noqa: E402

BASE = "http://localhost:5177"


async def api(c, path):
    return await c.eval("""(async () => {
      const r = await fetch('/api/v1%s',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      return Array.isArray(d) ? d.length : (d.content ? d.content.length : d);
    })()""" % path, awaitp=True)


async def click(c, selector, dy=24):
    box = await c.eval("""(() => {
      const e = document.querySelector(%r); if (!e) return null;
      const b = e.getBoundingClientRect();
      return {x: b.left + b.width/2, y: b.top + Math.min(b.height/2, %d)};
    })()""" % (selector, dy))
    assert box, f"не найден элемент {selector}"
    await c.click_xy(box["x"], box["y"])
    await asyncio.sleep(1.2)


async def main():
    c = await connect()
    await c.set_viewport(1440, 900)

    print("login purger:", await login(c, BASE, "purger", "password123"))
    print("бандлы:", await loaded_bundles(c))

    print("ДО очистки — Проектов:", await api(c, "/projects"),
          "Дел:", await api(c, "/delos"), "Целей:", await api(c, "/goals"))

    await c.goto(f"{BASE}/#/onboarding/final")
    await asyncio.sleep(1.5)
    print("экран Финального выбора:", await c.eval("document.querySelector('#final-choice-title')?.textContent"))

    await click(c, "[data-final-choice=purge]")
    await asyncio.sleep(3.0)
    print("после «Очистить» hash:", await c.eval("location.hash"))
    print("диалогов подтверждения:", await c.eval(
        "document.querySelectorAll('dialog[open],[role=alertdialog]').length"))

    print("ПОСЛЕ очистки — Проектов:", await api(c, "/projects"),
          "Дел:", await api(c, "/delos"), "Целей:", await api(c, "/goals"))
    print("Области жизни:", await api(c, "/life-areas"))
    print("Сферы:", await api(c, "/life-spheres"))
    print("норма (me):", await c.eval("""(async () => {
      const r = await fetch('/api/v1/auth/me',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      return {hours: d.availableWeeklyHours, onboardingCompleted: d.onboardingCompleted};
    })()""", awaitp=True))

    # экраны действительно пусты
    for route, label in (("/projects", "Проекты"), ("/delos", "Дела"), ("/goals", "Цели")):
        await c.goto(f"{BASE}/#{route}")
        await asyncio.sleep(1.6)
        rows = await c.eval(
            "document.querySelectorAll('tbody tr, li[class*=item], [class*=row][class*=project]').length")
        print(f"экран {label}: строк={rows} hash={await c.eval('location.hash')}")

    # мастер 0.4-08 запускается
    await c.goto(f"{BASE}/#/onboarding")
    await asyncio.sleep(1.6)
    print("мастер шаг:", await c.eval("document.querySelector('[data-wizard-step]')?.dataset.wizardStep"),
          "| заголовок:", await c.eval("document.querySelector('#wizard-title')?.textContent"))
    await c.screenshot("/tmp/ticket04-wizard.png")

    # шаг 1 — Проект
    await c.eval("""(() => {
      const i = document.querySelector('[data-wizard-step="1"] input');
      const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;
      s.call(i, 'Свой первый проект');
      i.dispatchEvent(new Event('input', {bubbles:true}));
    })()""")
    await click(c, '[data-wizard-step="1"] button[type=submit]')
    await asyncio.sleep(1.5)
    print("после шага 1 →", await c.eval("document.querySelector('[data-wizard-step]')?.dataset.wizardStep"))

    # шаг 2 — Цель + недельный бюджет
    await c.eval("""(() => {
      const ins = document.querySelectorAll('[data-wizard-step="2"] input');
      const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;
      s.call(ins[0], 'Продвинуть свой проект'); ins[0].dispatchEvent(new Event('input',{bubbles:true}));
      s.call(ins[1], '5'); ins[1].dispatchEvent(new Event('input',{bubbles:true}));
    })()""")
    await click(c, '[data-wizard-step="2"] button[type=submit]')
    await asyncio.sleep(1.5)
    print("после шага 2 →", await c.eval("document.querySelector('[data-wizard-step]')?.dataset.wizardStep"))

    # шаг 3 — норма
    await c.eval("""(() => {
      const i = document.querySelector('[data-wizard-step="3"] input');
      const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;
      s.call(i, '12'); i.dispatchEvent(new Event('input',{bubbles:true}));
    })()""")
    await click(c, '[data-wizard-step="3"] button[type=submit]')
    await asyncio.sleep(4.0)

    print("после мастера hash:", await c.eval("location.hash"))
    print("me:", await c.eval("""(async () => {
      const r = await fetch('/api/v1/auth/me',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      return {hours: d.availableWeeklyHours, onboardingCompleted: d.onboardingCompleted};
    })()""", awaitp=True))
    print("Проектов после мастера:", await api(c, "/projects"),
          "Целей:", await api(c, "/goals"))

    await c.goto(f"{BASE}/#/today")
    await asyncio.sleep(2.5)
    text = await c.eval("document.body.innerText")
    import re
    norm = [l for l in text.split("\n") if re.search(r"норм|остат|\dч|ч\b", l, re.I)][:12]
    print("«Сегодня» — строки об остатке нормы:", norm)
    await c.screenshot("/tmp/ticket04-today.png")


asyncio.run(main())
