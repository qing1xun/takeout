<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { couponStatusText, dateTime, type AnyRecord } from './customerShared'

const coupons = ref<AnyRecord[]>([])
const activeStatus = ref('ALL')
const error = ref('')
const busy = ref(false)

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'UNUSED', label: '未使用' },
  { key: 'LOCKED', label: '已锁定' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' },
]

const visibleCoupons = computed(() =>
  activeStatus.value === 'ALL'
    ? coupons.value
    : coupons.value.filter((coupon) => coupon.status === activeStatus.value),
)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    coupons.value = await api<AnyRecord[]>('CUSTOMER', '/user/coupons')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端 / 我的优惠券</p>
        <h1>券包状态和订单关联</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/customer/home">去领券</RouterLink>
        <RouterLink class="button-link" to="/customer/profile">返回我的</RouterLink>
      </div>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <nav class="customer-tabs" aria-label="优惠券状态筛选">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeStatus === tab.key }"
        @click="activeStatus = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section class="coupon-grid">
      <article v-for="coupon in visibleCoupons" :key="coupon.couponCode" class="coupon-card">
        <div>
          <span class="status-pill">{{ couponStatusText(coupon.status) }}</span>
          <h2>{{ coupon.title }}</h2>
          <p>{{ coupon.thresholdText }}</p>
        </div>
        <strong>{{ coupon.discountText }}</strong>
        <small>{{ coupon.couponCode }} / 批次 {{ coupon.batchCode }} / {{ coupon.scope }}</small>
        <small>有效期：{{ dateTime(coupon.validFrom) }} - {{ dateTime(coupon.validTo) }}</small>
        <small>{{ coupon.reason }}</small>
        <RouterLink v-if="coupon.relatedOrderId" class="button-link" :to="`/customer/orders/${coupon.relatedOrderId}`">
          查看关联订单 {{ coupon.relatedOrderNo }}
        </RouterLink>
        <div v-if="coupon.statusLogs?.length" class="coupon-log-list">
          <small v-for="log in coupon.statusLogs.slice(0, 3)" :key="log.id">
            {{ log.beforeStatus || 'INIT' }} → {{ log.afterStatus }} / {{ log.reason }} / {{ dateTime(log.createdAt) }}
          </small>
        </div>
      </article>
      <div v-if="visibleCoupons.length === 0" class="empty-state">当前没有该状态的优惠券</div>
    </section>
  </section>
</template>
