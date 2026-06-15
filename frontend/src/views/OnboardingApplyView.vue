<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, type AnyRecord } from './customer/customerShared'

const role = ref<'MERCHANT' | 'RIDER'>('MERCHANT')
const form = ref({
  applicantName: '',
  phone: '',
  storeName: '',
  category: '美食',
  address: '',
  preferredUsername: '',
  reason: '',
})
const result = ref<AnyRecord | null>(null)
const busy = ref(false)
const error = ref('')

const endpoint = computed(() =>
  role.value === 'MERCHANT' ? '/onboarding/merchant-applications' : '/onboarding/rider-applications',
)

async function submit() {
  busy.value = true
  error.value = ''
  try {
    result.value = await api<AnyRecord>(null, endpoint.value, {
      method: 'POST',
      body: { ...form.value },
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-card onboarding-card">
      <p class="eyebrow">入驻申请</p>
      <h1>商家和骑手审核入驻</h1>
      <p class="login-copy">提交后不会立即生成账号。后台审核通过后，系统创建商家或骑手账号。</p>

      <div class="customer-tabs">
        <button type="button" :class="{ active: role === 'MERCHANT' }" @click="role = 'MERCHANT'">商家入驻</button>
        <button type="button" :class="{ active: role === 'RIDER' }" @click="role = 'RIDER'">骑手入驻</button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>申请人</span>
          <input v-model="form.applicantName" placeholder="姓名/联系人" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" placeholder="11 位手机号" required />
        </label>
        <template v-if="role === 'MERCHANT'">
          <label>
            <span>门店名称</span>
            <input v-model="form.storeName" placeholder="例如 张江测试餐厅" required />
          </label>
          <label>
            <span>经营品类</span>
            <select v-model="form.category">
              <option>美食</option>
              <option>甜点饮品</option>
              <option>超市便利</option>
              <option>水果买菜</option>
              <option>夜宵</option>
              <option>跑腿代购</option>
            </select>
          </label>
          <label>
            <span>门店地址</span>
            <input v-model="form.address" placeholder="城市/商圈/详细地址" required />
          </label>
        </template>
        <label>
          <span>期望账号</span>
          <input v-model="form.preferredUsername" placeholder="可选，例如 merchant_xxx / rider_xxx" />
        </label>
        <label>
          <span>申请说明</span>
          <textarea v-model="form.reason" placeholder="资质、经营范围、配送经验等"></textarea>
        </label>
        <button class="primary" type="submit" :disabled="busy">
          {{ busy ? '提交中' : '提交申请' }}
        </button>
      </form>

      <section v-if="result" class="notice">
        申请已提交：编号 {{ result.id }}，状态 {{ result.status }}，提交时间 {{ dateTime(result.createdAt) }}。
      </section>
      <div class="button-row">
        <RouterLink class="button-link" to="/merchant/login">商家登录</RouterLink>
        <RouterLink class="button-link" to="/rider/login">骑手登录</RouterLink>
        <RouterLink class="button-link" to="/customer/register">用户注册</RouterLink>
      </div>
      <div v-if="error" class="error">{{ error }}</div>
    </div>
  </section>
</template>
