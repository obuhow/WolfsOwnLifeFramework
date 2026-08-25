#!/usr/bin/env python3
"""Мобильная проверка Финального выбора (390x780) + тихий контракт 0.3."""
import asyncio
import os
import sys

sys.path.insert(0, os.path.expanduser("~/.hermes/skills/wolf-life-os/scripts"))
import cdp_driver

cdp_driver.CDP_HTTP = "http://127.0.0.1:9336"
from cdp_driver import connect, login  # noqa: E402

BASE = "http://localhost:5177"


async def main():
    c = await connect()
    await c.set_viewport(390, 780)
    print("login keeper:", await login(c, BASE, "keeper", "password123"))

    await c.goto(f"{BASE}/#/onboarding/final")
    await asyncio.sleep(1.8)

    print("заголовок:", await c.eval("document.querySelector('#final-choice-title')?.textContent"))
    geo = await c.eval("""(() => {
      const bs = [...document.querySelectorAll('[data-final-choice]')];
      const vw = document.documentElement.clientWidth;
      return bs.map(b => {
        const r = b.getBoundingClientRect();
        return {k: b.dataset.finalChoice,
                inViewport: r.left >= -1 && r.right <= vw + 1,
                overflowX: Math.round(r.right - vw),
                w: Math.round(r.width), h: Math.round(r.height)};
      });
    })()""")
    print("геометрия кнопок на мобильном:", geo)

    print("горизонтальный скролл страницы:", await c.eval(
        "document.documentElement.scrollWidth - document.documentElement.clientWidth"))

    # обе ветки кликабельны: под центром кнопки лежит именно она, ничего не перекрывает
    hit = await c.eval("""(() => {
      return [...document.querySelectorAll('[data-final-choice]')].map(b => {
        const r = b.getBoundingClientRect();
        const el = document.elementFromPoint(r.left + r.width/2, r.top + 20);
        return {k: b.dataset.finalChoice, reachable: !!(el && b.contains(el))};
      });
    })()""")
    print("достижимость кликом:", hit)

    quiet = await c.eval("""(() => ({
      progressBars: document.querySelectorAll('progress,[role=progressbar]').length,
      svgIcons: document.querySelectorAll('svg').length,
      stepCounter: /шаг\\s*\\d+\\s*из\\s*\\d+/i.test(document.body.innerText),
      emoji: /[\\u{1F300}-\\u{1FAFF}]/u.test(document.body.innerText),
    }))()""")
    print("тихий контракт (мобильный):", quiet)

    await c.screenshot("/tmp/ticket04-mobile.png")
    print("скриншот: /tmp/ticket04-mobile.png")


asyncio.run(main())
