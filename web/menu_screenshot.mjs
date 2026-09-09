/**
 * Скриншотная проверка меню слева (док открыт по умолчанию / сворачивается).
 * Запуск: node menu_screenshot.mjs [--login user:pass]
 * Сохраняет кадры в ./menu-shots/
 */
import { chromium } from '@playwright/test'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'

const BASE = process.env.BASE_URL || 'http://localhost:5174'
const OUT = new URL('./menu-shots/', import.meta.url).pathname

const shot = async (page, name) => {
  await page.screenshot({ path: OUT + name, fullPage: false })
  console.log('shot:', name)
}

const cred = process.argv.find((a) => a.startsWith('--login='))?.split('=')[1] || null

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(BASE + '/', { waitUntil: 'networkidle' })

// Логин, если передали креды и форма на месте.
if (cred) {
  const [u, p] = cred.split(':')
  const inputs = page.locator('input')
  const n = await inputs.count()
  console.log('login inputs:', n)
  if (n >= 2) {
    await inputs.nth(0).fill(u)
    await inputs.nth(1).fill(p)
    await page.getByRole('button', { name: /войти|вход|login/i }).first().click()
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1200)
  }
}

console.log('URL now:', page.url())

// Кадр 1: что видно после логина (док открыт по умолчанию?).
await shot(page, '01-after-login.png')

const menuBtn = page.locator('[data-tour-target="menu"]').first()
console.log('burger visible:', await menuBtn.isVisible())

const drawer = page.locator('#wolf-nav-menu')
console.log('menu visible:', await drawer.isVisible())

// Геометрия: меню слева? контент справа?
const geo = await drawer.boundingBox()
const main = await page.locator('.app-main').first().boundingBox()
console.log('menu box:', JSON.stringify(geo))
console.log('main box:', JSON.stringify(main))
if (geo && main) {
  console.log('menu левее контента:', geo.x < main.x, `(menu.x=${Math.round(geo.x)}, main.x=${Math.round(main.x)})`)
}

// Кадр 2: сворачиваем бургером.
await menuBtn.click()
await page.waitForTimeout(450)
await shot(page, '02-after-burger-close.png')
console.log('menu visible after click:', await drawer.isVisible())

// localStorage после сворачивания.
console.log('dock pref:', await page.evaluate(() => localStorage.getItem('wolf_nav_dock_open')))

// Кадр 3: разворачиваем обратно.
await menuBtn.click()
await page.waitForTimeout(450)
await shot(page, '03-after-burger-open.png')
console.log('menu visible after 2nd click:', await drawer.isVisible())

await browser.close()
