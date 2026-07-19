/**
 * API base URL for the SPA.
 * - Dev (Vite): default `/api/v1` (proxied to localhost:8080)
 * - Compose/nginx: `/api/v1` (proxied to api service)
 * Override with VITE_API_BASE at build/dev time if needed.
 */
export function apiBase() {
  const raw = import.meta.env.VITE_API_BASE || '/api/v1'
  return raw.replace(/\/$/, '')
}
