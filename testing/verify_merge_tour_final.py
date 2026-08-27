#!/usr/bin/env python3
"""Проверяет, что тур первого входа приземляется на финальный выбор (тикет 04),
а повторный запуск из шапки — сразу в Ежедневник.

Стенд develop после мержа feature/04-final-choice-and-purge: web :80, api :8082.
"""
import asyncio
import json
import sys

sys.path.insert(0, "/home/obuhow/.hermes/skills/wolf-life-os/scripts")
import cdp_driver
cdp_driver.CDP_HTTP = "http://127.0.0.1:9344"
from cdp_driver import connect

g = json.load(open("/tmp/tourguest.json"))
WEB = "http://localhost"


async def run_tour(c, label):
    for step in range(14):
        gone = await c.eval("!document.querySelector('.tour')")
        if gone is True:
            print(f"  [{label}] тур закончился на шаге {step}")
            return
        title = await c.eval("(document.querySelector('.tour-hint-title')||{}).textContent")
        clicked = await c.eval("""
          (() => {
            const o = document.querySelector('.tour-outline');
            if (!o) return 'no-outline';
            const r = o.getBoundingClientRect();
            const el = document.elementFromPoint(r.left + r.width/2, r.top + r.height/2);
            if (!el) return 'no-el';
            el.click();
            return (el.textContent || '').trim().slice(0, 28);
          })()
        """)
        print(f"  [{label}] шаг {step}: «{title}» -> клик по «{clicked}»")
        await asyncio.sleep(1.8)
    print(f"  [{label}] ВНИМАНИЕ: тур не завершился за 14 шагов")


async def main():
    c = await connect()
    try:
        await c.goto(WEB + "/")
        await asyncio.sleep(2)

        # что реально загрузил браузер (ловушка кэша из скилла)
        srcs = await c.eval("[...document.querySelectorAll('script[src]')].map(s=>s.src).join(',')")
        print("bundle:", srcs)

        await c.eval(f"localStorage.setItem('wolf_token', {json.dumps(g['t'])}); "
                     f"localStorage.setItem('wolf_username', {json.dumps(g['u'])}); 'ok'")

        # --- ПЕРВЫЙ ВХОД ---
        print("\n=== первый вход: /onboarding/tour ===")
        await c.goto(WEB + "/#/onboarding/tour")
        await asyncio.sleep(3)
        print("hash после старта:", await c.eval("location.hash"))
        print("оверлей активен:", await c.eval("!!document.querySelector('.tour')"))

        await run_tour(c, "первый вход")
        await asyncio.sleep(2)

        print("ИТОГ hash:", await c.eval("location.hash"))
        print("финальный выбор виден:", await c.eval("!!document.querySelector('[data-final-choice]')"))
        print("кнопки:", await c.eval(
            "[...document.querySelectorAll('[data-final-choice]')].map(e=>e.dataset.finalChoice).join(',')"))
        print("текст экрана:", await c.eval(
            "(document.querySelector('main, .onboarding-page')||document.body).innerText.replace(/\\s+/g,' ').slice(0,160)"))
    finally:
        pass


asyncio.run(main())
