<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { registerCustomer, roleHome } from '@/services/api'

const router = useRouter()
const form = ref({
  username: '',
  password: '',
  displayName: '',
  phone: '',
})
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const session = await registerCustomer({
      username: form.value.username.trim(),
      password: form.value.password,
      displayName: form.value.displayName.trim(),
      phone: form.value.phone.trim(),
    })
    await router.replace(roleHome(session.role))
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-card">
      <p class="eyebrow">用户注册</p>
      <h1>创建用户账号</h1>
      <p class="login-copy">注册后直接进入用户端。商家和骑手请走入驻申请，由后台审核后创建账号。</p>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>账号</span>
          <input v-model="form.username" autocomplete="username" placeholder="小写字母、数字、下划线" required />
        </label>
        <label>
          <span>昵称</span>
          <input v-model="form.displayName" placeholder="展示昵称" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" autocomplete="tel" placeholder="11 位手机号" required />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" autocomplete="new-password" placeholder="至少 6 位" required type="password" />
        </label>
        <button class="primary" type="submit" :disabled="busy">
          {{ busy ? '注册中' : '注册并登录' }}
        </button>
      </form>

      <div class="button-row">
        <RouterLink class="button-link" to="/customer/login">返回用户登录</RouterLink>
        <RouterLink class="button-link" to="/onboarding">商家/骑手入驻</RouterLink>
      </div>
      <div v-if="error" class="error">{{ error }}</div>
    </div>
  </section>
</template>
