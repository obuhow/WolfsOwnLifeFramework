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
