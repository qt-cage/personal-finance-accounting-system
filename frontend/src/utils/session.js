let generation = 0
let tabToken
let suspended = false

function storedToken() {
  return localStorage.getItem('token') || ''
}

export function captureSession() {
  if (tabToken === undefined) tabToken = storedToken()
  return { generation, token: tabToken }
}

export function isCurrentSession(session) {
  return !suspended && session?.generation === generation && session.token === tabToken && session.token === storedToken()
}

export function invalidateSession(rebind = true) {
  generation++
  suspended = !rebind
  if (rebind) tabToken = storedToken()
}

export function staleSessionError() {
  const error = new Error('Session changed')
  error.code = 'STALE_SESSION'
  return error
}
