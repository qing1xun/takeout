<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { api } from '@/services/api'
import { couponStatusText, dateTime, type AnyRecord } from '../customer/customerShared'

const activities = ref<AnyRecord[]>([])
const coupons = ref<AnyRecord[]>([])
const logs = ref<AnyRecord[]>([])
const status = ref('')
const keyword = ref('')
const selectedCoupon = ref<AnyRecord | null>(null)
const page = ref({ page: 0, size: 10, total: 0, hasMore: false })
const busy = ref(false)
const error = ref('')

const statusTabs = [
  { key: '', label: '全部' },
  { key: 'UNUSED', label: '未使用' },
  { key: 'LOCKED', label: '锁定中' },
  { key: 'USED', label: '已核销' },
  { key: 'EXPIRED', label: '已过期' },
]

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const pageNumbers = computed(() => {
  const total = totalPages.value
  const current = page.value.page
  const start = Math.max(0, Math.min(current - 4, total - 10))
  const count = Math.min(10, total)
  return Array.from({ length: count }, (_, index) => start + index)
})

loadAll()

async function loadAll() {
  await Promise.all([loadActivities(), loadCoupons(0), loadLogs()])
}

async function loadActivities() {
  activities.value = await api<AnyRecord[]>('ADMIN', '/admin/coupon-activities')
}

async function loadCoupons(pageNo = page.value.page) {
  busy.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(Math.max(0, pageNo)))
    params.set('size', String(page.value.size))
    if (status.value) {
      params.set('status', status.value)
    }
    if (keyword.value.trim()) {
      params.set('keyword', keyword.value.trim())
    }
    const result = await api<AnyRecord>('ADMIN', `/admin/coupons/page?${params}`)
    coupons.value = result.records
    page.value = { page: result.page, size: result.size, total: result.total, hasMore: result.hasMore }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function loadLogs(couponId?: number) {
  const params = new URLSearchParams()
  params.set('limit', '80')
  if (couponId) {
    params.set('couponId', String(couponId))
  }
  logs.value = await api<AnyRecord[]>('ADMIN', `/admin/coupon-status-logs?${params}`)
}

async function setStatus(value: string) {
  status.value = value
  selectedCoupon.value = null
  await loadCoupons(0)
  await loadLogs()
}

async function search() {
  selectedCoupon.value = null
  await loadCoupons(0)
  await loadLogs()
}

async function inspectCoupon(coupon: AnyRecord) {
  selectedCoupon.value = coupon
  await loadLogs(coupon.id)
}

async function clearSelectedCoupon() {
  selectedCoupon.value = null
  await loadLogs()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">后台端 / 优惠券治理</p>
        <h1>红包活动、券实例和状态日志</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/admin">后台首页</RouterLink>
        <RouterLink class="button-link" to="/admin/merchant-accounts">商家账号映射</RouterLink>
        <RouterLink class="button-link" to="/admin/onboarding-applications">入驻审核</RouterLink>
        <PortalLogoutButton role="ADMIN" />
      </div>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel wide-panel">
      <div class="panel-heading">
        <h2>活动批次</h2>
        <span>{{ activities.length }} 个</span>
      </div>
      <div class="data-list dense">
        <article v-for="activity in activities" :key="activity.batchCode" class="compact-row">
          <div>
            <strong>{{ activity.title }} · {{ activity.batchCode }}</strong>
            <span>{{ activity.sceneCode }} / {{ dateTime(activity.startAt) }} - {{ dateTime(activity.endAt) }}</span>
          </div>
          <span>
            库存 {{ activity.remainingStock }}/{{ activity.stock }} · 领包 {{ activity.claimedPacks }} · 发券 {{ activity.issuedCoupons }}
            · 锁定 {{ activity.lockedCoupons }} · 核销 {{ activity.usedCoupons }}
          </span>
        </article>
      </div>
    </section>

    <div class="admin-layout">
      <section class="panel">
        <div class="panel-heading">
          <h2>券实例</h2>
          <span>第 {{ page.page + 1 }} / {{ totalPages }} 页，共 {{ page.total }} 张</span>
        </div>
        <div class="button-row">
          <button
            v-for="tab in statusTabs"
            :key="tab.key"
            type="button"
            :class="{ active: status === tab.key }"
            @click="setStatus(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
        <label class="field">
          <span>搜索</span>
          <input v-model="keyword" placeholder="券码、批次、用户、手机号、订单号" @keyup.enter="search" />
        </label>
        <div class="button-row">
          <button type="button" @click="search">查询</button>
          <button type="button" @click="keyword = ''; search()">清空</button>
        </div>

        <div class="data-list dense">
          <article v-for="coupon in coupons" :key="coupon.id" class="compact-row coupon-admin-row">
            <div>
              <strong>{{ coupon.couponCode }} · {{ couponStatusText(coupon.status) }}</strong>
              <span>{{ coupon.userName }} / {{ coupon.phoneMasked }} / {{ coupon.title }} / {{ coupon.batchCode }}</span>
              <span v-if="coupon.relatedOrderNo">关联订单 {{ coupon.relatedOrderNo }}</span>
              <span>{{ coupon.reason }}</span>
            </div>
            <button type="button" @click="inspectCoupon(coupon)">日志</button>
          </article>
          <div v-if="coupons.length === 0" class="empty-state">暂无券实例</div>
        </div>

        <nav v-if="totalPages > 1" class="pager" aria-label="优惠券分页">
          <button type="button" :disabled="page.page === 0" @click="loadCoupons(page.page - 1)">‹</button>
          <button
            v-for="pageNo in pageNumbers"
            :key="pageNo"
            type="button"
            :class="{ active: page.page === pageNo }"
            @click="loadCoupons(pageNo)"
          >
            {{ pageNo + 1 }}
          </button>
          <button type="button" :disabled="page.page >= totalPages - 1" @click="loadCoupons(page.page + 1)">›</button>
        </nav>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h2>状态日志</h2>
          <span>{{ selectedCoupon ? selectedCoupon.couponCode : '最新' }}</span>
        </div>
        <div v-if="selectedCoupon" class="button-row">
          <button type="button" @click="clearSelectedCoupon">查看最新日志</button>
        </div>
        <div class="data-list dense">
          <article v-for="log in logs" :key="log.id" class="compact-row">
            <div>
              <strong>{{ log.beforeStatus || 'INIT' }} → {{ log.afterStatus }}</strong>
              <span>{{ log.couponCode }} / {{ log.userName }} / {{ log.operatorName }}</span>
              <span v-if="log.relatedOrderNo">订单 {{ log.relatedOrderNo }}</span>
              <span>{{ log.reason }}</span>
            </div>
            <span>{{ dateTime(log.createdAt) }}</span>
          </article>
          <div v-if="logs.length === 0" class="empty-state">暂无状态日志</div>
        </div>
      </section>
    </div>
  </section>
</template>
