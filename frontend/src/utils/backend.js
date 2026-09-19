const defaultOrigin = import.meta.env.DEV ? 'http://localhost:8088' : window.location.origin

export const BACKEND_ORIGIN = (import.meta.env.VITE_API_ORIGIN || defaultOrigin).replace(/\/+$/, '')
export const API_BASE_URL = `${BACKEND_ORIGIN}/api`

/** 将后端返回的相对资源路径转换为浏览器可访问地址。 */
export function resolveBackendAssetUrl(path) {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  return `${BACKEND_ORIGIN}/${String(path).replace(/^\/+/, '')}`
}
