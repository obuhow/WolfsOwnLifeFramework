/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * API base URL for the SPA.
 * - Dev (Vite): default `/api/v1` (proxied to localhost:8082 — see vite.config.js)
 * - Compose/nginx: `/api/v1` baked at image build (Dockerfile ARG), nginx proxies to api
 * Override with VITE_API_BASE at build/dev time if needed.
 */
export function apiBase() {
  const raw = import.meta.env.VITE_API_BASE || '/api/v1'
  return raw.replace(/\/$/, '')
}

const TOKEN_KEY = 'wolf_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * Build Authorization headers. On missing token → redirect to login.
 * @param {boolean} json
 * @returns {Record<string, string>|null}
 */
export function authHeaders(json = false) {
  const token = getToken()
  if (!token) {
    window.location.hash = '#/login'
    return null
  }
  const headers = { Authorization: `Bearer ${token}` }
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

/**
 * If response is 401/403, drop stale JWT and send user to login.
 * Spring Security often returns 403 (not 401) for unauthenticated API calls.
 * @param {Response} res
 * @returns {boolean} true if auth was cleared / redirect triggered
 */
export function handleAuthFailure(res) {
  if (res && (res.status === 401 || res.status === 403)) {
    clearAuth()
    if (!window.location.hash.includes('/login')) {
      window.location.hash = '#/login'
      // force remount so App.vue drops the shell
      window.location.reload()
    }
    return true
  }
  return false
}
