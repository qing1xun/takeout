<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, money, statusText, type AnyRecord } from '../customer/customerShared'

const orders = ref<AnyRecord[]>([])
const page = ref({ page: 0, size: 8, total: 0, hasMore: false })
const activeStatus = ref('ALL')
const notice = ref('')
const error = ref('')
const busy = ref(false)
const rejectDialog = ref({ open: false, order: null as AnyRecord | null, reason: '' })

const tabs = [
  { key: 'ALL', label: '全部', statuses: [] },
  { key: 'PAID_WAIT_ACCEPT', label: '待接单', statuses: ['PAID_WAIT_ACCEPT'] },
  { key: 'PREPARING', label: '备餐中', statuses: ['PREPARING'] },
  { key: 'WAIT_PICKUP', label: '待骑手', statuses: ['WAIT_PICKUP'] },
  { key: 'DONE', label: '已结束', statuses: ['DELIVERED', 'COMPLETED', 'REFUNDED', 'CANCELLED'] },
]

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const pageNumbers = computed(() => Array.from({ length: Math.min(10, totalPages.value) }, (_, index) => index))
const visibleOrders = computed(() => {
  const tab = tabs.find((item) => item.key === activeStatus.value)
  if (!tab || tab.statuses.length === 0) {
    return orders.value
  }
  return orders.value.filter((order) => tab.statuses.includes(order.status))
})

load(0)

async function load(pageNo = 0) {
  busy.value = true
  error.value = ''
  try {
    const result = await api<AnyRecord>('MERCHANT', `/merchant/orders/page?page=${Math.max(0, pageNo)}&size=${page.value.size}`)
    orders.value = result.records
    page.value = { page: result.page, size: result.size, total: result.total, hasMore: result.hasMore }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function accept(order: AnyRecord) {
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${order.id}/accept`, { method: 'POST' })
  notice.value = `${order.orderNo} 已接单`
  await load(page.value.page)
}

async function ready(order: AnyRecord) {
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${order.id}/ready`, { method: 'POST' })
  notice.value = `${order.orderNo} 已出餐，配送任务进入骑手可接池`
  await load(page.value.page)
}

function openReject(order: AnyRecord) {
  rejectDialog.value = { open: true, order, reason: '' }
}

async function submitReject() {
  if (!rejectDialog.value.order) {
    return
  }
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${rejectDialog.value.order.id}/reject`, {
    method: 'POST',
    body: { reason: rejectDialog.value.reason || '商家无法履约，拒单退款' },
  })
  notice.value = `${rejectDialog.value.order.orderNo} 已拒单并退款`
  rejectDialog.value.open = false
  await load(page.value.page)
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">商家端 / 订单处理</p>
        <h1>接单、拒单、出餐和状态日志</h1>
      </div>
      <RouterLink class="button-link" to="/merchant/dashboard">返回概览</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <nav class="customer-tabs" aria-label="商家订单状态筛选">
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

    <section class="order-grid">
      <article v-for="order in visibleOrders" :key="order.id" class="order-card">
        <div class="order-main">
          <div>
            <span class="status-pill">{{ statusText(order.status) }}</span>
            <h3>{{ order.orderNo }} · {{ order.customerName }}</h3>
            <p>{{ order.addressSnapshot?.detailMasked }} / {{ dateTime(order.createdAt) }}</p>
          </div>
          <strong>{{ money(order.payAmount) }}</strong>
        </div>
        <div class="tracking-line">
          <span v-for="record in order.statusRecords" :key="record.id">
            {{ statusText(record.afterStatus) }} · {{ record.operator }} · {{ record.reason }}
          </span>
        </div>
        <div class="data-list">
          <article v-for="item in order.items" :key="item.productId" class="mini-row">
            <strong>{{ item.productName }}</strong>
            <span>{{ money(item.price) }} x {{ item.quantity }}</span>
          </article>
        </div>
        <div class="row-actions">
          <button v-if="order.status === 'PAID_WAIT_ACCEPT'" type="button" @click="accept(order)">接单</button>
          <button v-if="order.status === 'PAID_WAIT_ACCEPT'" type="button" @click="openReject(order)">拒单退款</button>
          <button v-if="order.status === 'PREPARING'" type="button" @click="ready(order)">出餐</button>
        </div>
      </article>
      <div v-if="visibleOrders.length === 0" class="empty-state">当前筛选下暂无订单</div>
    </section>

    <nav v-if="totalPages > 1" class="pager" aria-label="订单分页">
      <button type="button" :disabled="page.page === 0" @click="load(page.page - 1)">‹</button>
      <button
        v-for="pageNo in pageNumbers"
        :key="pageNo"
        type="button"
        :class="{ active: page.page === pageNo }"
        @click="load(pageNo)"
      >
        {{ pageNo + 1 }}
      </button>
      <button type="button" :disabled="page.page >= totalPages - 1" @click="load(page.page + 1)">›</button>
    </nav>

    <div v-if="rejectDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">拒单退款</p>
            <h2>{{ rejectDialog.order?.orderNo }}</h2>
          </div>
          <button type="button" @click="rejectDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>拒单原因</span>
          <textarea v-model="rejectDialog.reason" placeholder="例如库存不足、门店临时打烊、无法按时出餐"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="rejectDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitReject">确认拒单</button>
        </div>
      </section>
    </div>
  </section>
</template>
