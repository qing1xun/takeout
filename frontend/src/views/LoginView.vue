<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { login, roleHome, roleLabel, type Role } from '@/services/api'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

const portal = computed<Role>(() => (route.meta.portal as Role) || 'CUSTOMER')
const portalName = computed(() => roleLabel(portal.value))
const redirect = computed(() => String(route.query.redirect || ''))
const loginLinks: Array<{ role: Role; path: string; label: string }> = [
  { role: 'CUSTOMER', path: '/customer/login', label: '用户端' },
  { role: 'MERCHANT', path: '/merchant/login', label: '商家端' },
  { role: 'RIDER', path: '/rider/login', label: '骑手端' },
  { role: 'ADMIN', path: '/admin/login', label: '后台端' },
]

async function submitLogin() {
  busy.value = true
  error.value = ''
  try {
    const session = await login(portal.value, { username: username.value.trim(), password: password.value })
    const target = validRedirect(redirect.value, session.role) ? redirect.value : roleHome(session.role)
    await router.replace(target)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function validRedirect(value: string, role: Role) {
  const prefixes: Record<Role, string> = {
    CUSTOMER: '/customer',
    MERCHANT: '/merchant',
    RIDER: '/rider',
    ADMIN: '/admin',
  }
  const prefix = prefixes[role]
  return value === prefix || value.startsWith(`${prefix}/`)
}
</script>

<template>
  <section class="login-page">
    <div class="login-card">
      <p class="eyebrow">{{ portalName }}登录</p>
      <h1>进入{{ portalName }}</h1>
      <p class="login-copy">此入口只接受{{ portalName }}账号。其他角色账号会被拒绝，避免跨端登录态混用。</p>

      <nav class="customer-tabs">
        <RouterLink
          v-for="item in loginLinks"
          :key="item.role"
          :class="{ active: portal === item.role }"
          :to="item.path"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <form class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>账号</span>
          <input v-model="username" autocomplete="username" placeholder="请输入账号" required />
        </label>
        <label>
          <span>密码</span>
          <input v-model="password" autocomplete="current-password" placeholder="请输入密码" required type="password" />
        </label>
        <button class="primary" type="submit" :disabled="busy">
          {{ busy ? '登录中' : '登录' }}
        </button>
      </form>

      <div class="button-row">
        <RouterLink class="button-link" to="/customer/register">用户注册</RouterLink>
        <RouterLink class="button-link" to="/onboarding">商家/骑手入驻申请</RouterLink>
      </div>
      <div v-if="error" class="error">{{ error }}</div>
    </div>
  </section>
</template>
