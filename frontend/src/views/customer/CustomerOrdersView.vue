<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, money, orderCanRefund, statusText, type AnyRecord } from './customerShared'

const orders = ref<AnyRecord[]>([])
const activeTab = ref('ALL')
const notice = ref('')
const error = ref('')
const busy = ref(false)
const refundDialog = ref({ open: false, orderId: 0, orderNo: '', reason: '' })
const reviewDialog = ref({ open: false, orderId: 0, orderNo: '', score: 5, content: '' })

const tabs = [
  { key: 'ALL', label: '全部', statuses: [] },
  { key: 'WAIT_PAY', label: '待支付', statuses: ['WAIT_PAY'] },
  { key: 'RUNNING', label: '进行中', statuses: ['PAID_WAIT_ACCEPT', 'PREPARING', 'WAIT_PICKUP', 'DELIVERING'] },
  { key: 'DELIVERED', label: '待确认', statuses: ['DELIVERED'] },
  { key: 'COMPLETED', label: '已完成', statuses: ['COMPLETED'] },
  { key: 'AFTERSALE', label: '售后', statuses: ['REFUNDING', 'REFUNDED', 'AFTERSALE'] },
  { key: 'CLOSED', label: '已关闭', statuses: ['CANCELLED'] },
]

const visibleOrders = computed(() => {
  const tab = tabs.find((item) => item.key === activeTab.value)
  if (!tab || tab.statuses.length === 0) {
    return orders.value
  }
  return orders.value.filter((order) => tab.statuses.includes(order.status))
})

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    orders.value = await api<AnyRecord[]>('CUSTOMER', '/user/orders')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function pay(orderId: number) {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/pay`, { method: 'POST', body: {} })
  notice.value = '支付成功，等待商家接单'
  await load()
}

async function confirm(orderId: number) {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/confirm`, { method: 'POST' })
  notice.value = '已确认收货，可以评价订单'
  await load()
}

function openRefund(order: AnyRecord) {
  refundDialog.value = { open: true, orderId: order.id, orderNo: order.orderNo, reason: '' }
}

async function submitRefund() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${refundDialog.value.orderId}/refund`, {
    method: 'POST',
    body: { reason: refundDialog.value.reason || '用户申请退款/取消订单' },
  })
  refundDialog.value.open = false
  notice.value = '退款或售后申请已提交'
  await load()
}

function openReview(order: AnyRecord) {
  reviewDialog.value = { open: true, orderId: order.id, orderNo: order.orderNo, score: 5, content: '' }
}

async function submitReview() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${reviewDialog.value.orderId}/review`, {
    method: 'POST',
    body: { score: reviewDialog.value.score, content: reviewDialog.value.content },
  })
  reviewDialog.value.open = false
  notice.value = '评价已提交，商家详情页和我的评价可查看'
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端 / 我的订单</p>
        <h1>订单查询、支付、确认、评价和售后</h1>
      </div>
      <RouterLink class="button-link" to="/customer/profile">返回我的</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <nav class="customer-tabs" aria-label="订单状态筛选">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section class="order-grid">
      <article v-for="order in visibleOrders" :key="order.id" class="order-card">
        <div class="order-main">
          <div>
            <span class="status-pill">{{ statusText(order.status) }}</span>
            <h3>{{ order.storeName }} · {{ order.orderNo }}</h3>
            <p>{{ dateTime(order.createdAt) }} / {{ order.addressSnapshot?.detailMasked }}</p>
          </div>
          <strong>{{ money(order.payAmount) }}</strong>
        </div>
        <div class="tracking-line">
          <span v-for="record in order.statusRecords" :key="record.id">
            {{ statusText(record.afterStatus) }} · {{ record.reason }}
          </span>
        </div>
        <div class="row-actions">
          <RouterLink class="button-link" :to="`/customer/orders/${order.id}`">查看详情</RouterLink>
          <button v-if="order.status === 'WAIT_PAY'" type="button" @click="pay(order.id)">支付</button>
          <button v-if="order.status === 'DELIVERED'" type="button" @click="confirm(order.id)">确认收货</button>
          <button v-if="order.status === 'COMPLETED' && !order.review" type="button" @click="openReview(order)">评价</button>
          <button v-if="orderCanRefund(order.status)" type="button" @click="openRefund(order)">
            {{ order.status === 'WAIT_PAY' ? '取消订单' : '申请售后' }}
          </button>
        </div>
      </article>
      <div v-if="visibleOrders.length === 0" class="empty-state">当前筛选下没有订单</div>
    </section>

    <div v-if="refundDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">售后申请</p>
            <h2>{{ refundDialog.orderNo }}</h2>
          </div>
          <button type="button" @click="refundDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>原因说明</span>
          <textarea v-model="refundDialog.reason" placeholder="请填写取消/退款/售后原因"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="refundDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitRefund">提交</button>
        </div>
      </section>
    </div>

    <div v-if="reviewDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">订单评价</p>
            <h2>{{ reviewDialog.orderNo }}</h2>
          </div>
          <button type="button" @click="reviewDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>评分</span>
          <select v-model.number="reviewDialog.score">
            <option v-for="score in [5, 4, 3, 2, 1]" :key="score" :value="score">{{ score }} 分</option>
          </select>
        </label>
        <label class="field">
          <span>评价内容</span>
          <textarea v-model="reviewDialog.content" placeholder="口味、包装、配送体验"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="reviewDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitReview">提交评价</button>
        </div>
      </section>
    </div>
  </section>
</template>
