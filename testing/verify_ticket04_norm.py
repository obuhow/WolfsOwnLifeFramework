#!/usr/bin/env python3
"""Проверка, что введённая в мастере норма реально сохранилась и видна в интерфейсе.

Отдельный виджет «остаток дневной нормы» на «Сегодня» (тикет 0.4-07) в коде
отсутствует и на develop — это чужой пробел, не регрессия тикета 04. Здесь
проверяется то, что тикет 04 действительно контролирует: значение нормы,
введённое пользователем в мастере после очистки, сохранено и доступно.
"""
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
    await c.set_viewport(1440, 900)
    print("login purger:", await login(c, BASE, "purger", "password123"))

    settings = await c.eval("""(async () => {
      const r = await fetch('/api/v1/settings',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      return await r.json();
    })()""", awaitp=True)
    print("настройки (норма из мастера):", settings)

    await c.goto(f"{BASE}/#/settings")
    await asyncio.sleep(2.5)
    shown = await c.eval("""(() => {
      const inputs = [...document.querySelectorAll('input')].map(i => i.value);
      const t = document.body.innerText;
      return {has12: inputs.includes('12') || inputs.includes('12.00') || /\\b12\\b/.test(t),
              inputs: inputs.slice(0, 12)};
    })()""")
    print("экран Настроек:", shown)
    await c.screenshot("/tmp/ticket04-settings.png")

    goals = await c.eval("""(async () => {
      const r = await fetch('/api/v1/goals',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      const arr = Array.isArray(d) ? d : (d.content || []);
      return arr.map(g => g.title);
    })()""", awaitp=True)
    print("Цели пользователя (введены в мастере):", goals)

    projects = await c.eval("""(async () => {
      const r = await fetch('/api/v1/projects',
        {headers:{Authorization:'Bearer '+localStorage.getItem('wolf_token')}});
      const d = await r.json();
      const arr = Array.isArray(d) ? d : (d.content || []);
      return arr.map(p => p.title);
    })()""", awaitp=True)
    print("Проекты пользователя (введены в мастере):", projects)


asyncio.run(main())
