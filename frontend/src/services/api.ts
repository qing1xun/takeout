export type Role = 'CUSTOMER' | 'MERCHANT' | 'RIDER' | 'ADMIN'

export interface Session {
  token: string
  userId: number
  username: string
  displayName: string
  role: Role
  portal: Role
  storeId?: number
  expiresAt?: string
}

export interface LoginCredential {
  username: string
  password: string
}

export interface CustomerRegisterPayload {
  username: string
  password: string
  displayName: string
  phone: string
}

type ApiOptions = Omit<RequestInit, 'body'> & { body?: unknown }

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

const roles: Role[] = ['CUSTOMER', 'MERCHANT', 'RIDER', 'ADMIN']
const legacySessionKey = 'takeout.session'
const activeSessions: Record<Role, Session | null> = {
  CUSTOMER: readStoredSession('CUSTOMER'),
  MERCHANT: readStoredSession('MERCHANT'),
  RIDER: readStoredSession('RIDER'),
  ADMIN: readStoredSession('ADMIN'),
}

localStorage.removeItem(legacySessionKey)

export function getSession(role?: Role) {
  if (role) {
    return activeSessions[role]
  }
  return roles.map((item) => activeSessions[item]).find(Boolean) || null
}

export function hasSessionForRole(role: Role) {
  return activeSessions[role]?.role === role
}

export function roleHome(role: Role) {
  const routes: Record<Role, string> = {
    CUSTOMER: '/customer/home',
    MERCHANT: '/merchant/dashboard',
    RIDER: '/rider',
    ADMIN: '/admin',
  }
  return routes[role]
}

export function roleLogin(role: Role) {
  const routes: Record<Role, string> = {
    CUSTOMER: '/customer/login',
    MERCHANT: '/merchant/login',
    RIDER: '/rider/login',
    ADMIN: '/admin/login',
  }
  return routes[role]
}

export function roleLabel(role: Role) {
  const labels: Record<Role, string> = {
    CUSTOMER: '用户端',
    MERCHANT: '商家端',
    RIDER: '骑手端',
    ADMIN: '后台端',
  }
  return labels[role]
}

export async function login(portal: Role, credential: LoginCredential): Promise<Session> {
  const session = await request<Session>('/auth/login', {
    method: 'POST',
    body: { ...credential, portal },
  })
  if (session.role !== portal || session.portal !== portal) {
    throw new Error('当前账号不能登录该端')
  }
  setSession(portal, session)
  return session
}

export async function registerCustomer(payload: CustomerRegisterPayload): Promise<Session> {
  const session = await request<Session>('/auth/register/customer', {
    method: 'POST',
    body: payload,
  })
  setSession('CUSTOMER', session)
  return session
}

export async function refreshSession(role: Role): Promise<Session | null> {
  const token = activeSessions[role]?.token
  if (!token) {
    return null
  }
  try {
    const session = await request<Session>('/auth/me', { method: 'GET' }, token)
    if (session.role !== role || session.portal !== role) {
      clearSession(role)
      return null
    }
    setSession(role, session)
    return session
  } catch {
    clearSession(role)
    return null
  }
}

export async function logout(role: Role): Promise<void> {
  const token = activeSessions[role]?.token
  clearSession(role)
  if (!token) {
    return
  }
  try {
    await request<null>('/auth/logout', { method: 'POST' }, token)
  } catch {
    // Local session is already cleared; server-side token cleanup is best effort.
  }
}

export async function logoutAll(role: Role): Promise<void> {
  const token = activeSessions[role]?.token
  clearSession(role)
  if (!token) {
    return
  }
  try {
    await request<null>('/auth/logout-all', { method: 'POST' }, token)
  } catch {
    // Local session is already cleared; server-side token cleanup is best effort.
  }
}

export function requireSession(role: Role): Session {
  const session = activeSessions[role]
  if (!session) {
    throw new Error('请先登录')
  }
  if (session.role !== role || session.portal !== role) {
    clearSession(role)
    throw new Error(`当前账号不能登录${roleLabel(role)}`)
  }
  return session
}

export async function api<T>(
  role: Role | null,
  path: string,
  options: ApiOptions = {},
  _credential?: LoginCredential,
) {
  const session = role ? requireSession(role) : null
  try {
    return await request<T>(path, options, session?.token)
  } catch (error) {
    if (role && error instanceof Error && (error.message.includes('请先登录') || error.message.includes('登录已过期'))) {
      clearSession(role)
    }
    throw error
  }
}

function setSession(role: Role, session: Session) {
  activeSessions[role] = session
  localStorage.setItem(sessionStorageKey(role), JSON.stringify(session))
}

export function clearSession(role: Role) {
  activeSessions[role] = null
  localStorage.removeItem(sessionStorageKey(role))
}

function readStoredSession(role: Role): Session | null {
  const key = sessionStorageKey(role)
  const raw = localStorage.getItem(key)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Session
    if (!parsed.token || parsed.role !== role || parsed.portal !== role) {
      localStorage.removeItem(key)
      return null
    }
    if (parsed.expiresAt && new Date(parsed.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(key)
      return null
    }
    return parsed
  } catch {
    localStorage.removeItem(key)
    return null
  }
}

function sessionStorageKey(role: Role) {
  return `auth.${role.toLowerCase()}.session`
}

async function request<T>(
  path: string,
  options: ApiOptions = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`/api${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || `HTTP ${response.status}`)
  }
  return payload.data
}
