#!/usr/bin/env python3
"""Повторный запуск тура из шапки не должен вести на финальный выбор.

Пользователь уже живёт в системе; кнопка «Очистить» удалила бы его настоящие
данные. Ожидание: тур заканчивается в Ежедневнике (/calendar?view=week).
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


async def main():
    c = await connect()

    # гость уже завершил онбординг веткой «Оставить» (см. шаг ниже)
    await c.goto(WEB + "/")
    await asyncio.sleep(2)
    await c.eval(f"localStorage.setItem('wolf_token', {json.dumps(g['t'])}); "
                 f"localStorage.setItem('wolf_username', {json.dumps(g['u'])}); 'ok'")

    await c.goto(WEB + "/#/calendar?view=week")
    await asyncio.sleep(3)
    print("стартовый hash:", await c.eval("location.hash"))

    # жмём «Приветственный тур» в шапке
    started = await c.eval("""
      (() => {
        const b = [...document.querySelectorAll('button')]
          .find(x => (x.textContent||'').includes('Приветственный тур'));
        if (!b) return 'кнопка не найдена';
        b.click();
        return 'клик';
      })()
    """)
    print("повторный запуск:", started)
    await asyncio.sleep(2.5)
    print("оверлей активен:", await c.eval("!!document.querySelector('.tour')"))

    for step in range(14):
        if await c.eval("!document.querySelector('.tour')") is True:
            print(f"тур закончился на шаге {step}")
            break
        title = await c.eval("(document.querySelector('.tour-hint-title')||{}).textContent")
        clicked = await c.eval("""
          (() => {
            const o = document.querySelector('.tour-outline');
            if (!o) return 'no-outline';
            const r = o.getBoundingClientRect();
            const el = document.elementFromPoint(r.left + r.width/2, r.top + r.height/2);
            if (!el) return 'no-el';
            el.click();
            return (el.textContent || '').trim().slice(0, 26);
          })()
        """)
        print(f"  шаг {step}: «{title}» -> «{clicked}»")
        await asyncio.sleep(1.8)

    await asyncio.sleep(2)
    final_hash = await c.eval("location.hash")
    on_final = await c.eval("!!document.querySelector('[data-final-choice]')")
    print("ИТОГ hash:", final_hash)
    print("финальный выбор показан:", on_final, "(ожидалось False)")
    print("ВЕРДИКТ:", "OK" if (not on_final and 'calendar' in str(final_hash)) else "ПРОВАЛ")


asyncio.run(main())
