<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { api } from '@/services/api'
import { dateTime, money, statusText, ticketStatusText, type AnyRecord } from '../customer/customerShared'

const store = ref<AnyRecord | null>(null)
const products = ref<AnyRecord>({ records: [], total: 0 })
const orders = ref<AnyRecord>({ records: [], total: 0 })
const reviews = ref<AnyRecord[]>([])
const tickets = ref<AnyRecord[]>([])
const error = ref('')
const busy = ref(false)

const lowStockCount = computed(() => (products.value.records || []).filter((item: AnyRecord) => item.stock < 20).length)
const pendingOrderCount = computed(() =>
  (orders.value.records || []).filter((order: AnyRecord) => order.status === 'PAID_WAIT_ACCEPT').length,
)
const pendingTicketCount = computed(() => tickets.value.filter((ticket) => ticket.status === 'PENDING').length)
const unrepliedReviewCount = computed(() => reviews.value.filter((review) => !review.merchantReply).length)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    const [storeResult, productResult, orderResult, reviewResult, ticketResult] = await Promise.all([
      api<AnyRecord>('MERCHANT', '/merchant/store'),
      api<AnyRecord>('MERCHANT', '/merchant/products/page?page=0&size=6'),
      api<AnyRecord>('MERCHANT', '/merchant/orders/page?page=0&size=6'),
      api<AnyRecord[]>('MERCHANT', '/merchant/reviews'),
      api<AnyRecord[]>('MERCHANT', '/merchant/after-sales'),
    ])
    store.value = storeResult
    products.value = productResult
    orders.value = orderResult
    reviews.value = reviewResult
    tickets.value = ticketResult
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
        <p class="eyebrow">商家端 / 经营概览</p>
        <h1>{{ store?.name || '门店经营中心' }}</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/merchant/store-settings">门店设置</RouterLink>
        <PortalLogoutButton role="MERCHANT" />
      </div>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section v-if="store" class="ops-hero">
      <div>
        <p class="eyebrow">{{ store.open ? '营业中' : '已打烊' }}</p>
        <h2>{{ store.statusMessage }}</h2>
        <p>{{ store.notice }} / 营业 {{ store.businessHours }} / 配送 {{ store.deliveryRangeKm }}km</p>
      </div>
      <div class="ops-metrics">
        <span>评分 {{ store.rating }}</span>
        <span>月售 {{ store.monthlySales }}</span>
        <span>起送 {{ money(store.minDeliveryAmount) }}</span>
        <span>配送费 {{ money(store.deliveryFee) }}</span>
      </div>
    </section>

    <section class="kpi-row">
      <article class="kpi">
        <span>待接订单</span>
        <strong>{{ pendingOrderCount }}</strong>
      </article>
      <article class="kpi">
        <span>商品总数</span>
        <strong>{{ products.total || 0 }}</strong>
      </article>
      <article class="kpi">
        <span>待回复评价</span>
        <strong>{{ unrepliedReviewCount }}</strong>
      </article>
      <article class="kpi">
        <span>售后关注</span>
        <strong>{{ pendingTicketCount }}</strong>
      </article>
    </section>

    <section class="profile-grid">
      <RouterLink class="profile-entry" to="/merchant/orders">
        <strong>订单处理</strong>
        <span>接单、拒单、出餐，状态写入订单日志</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/merchant/products">
        <strong>商品管理</strong>
        <span>新增、编辑、上下架、库存维护；当前低库存 {{ lowStockCount }}</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/merchant/reviews">
        <strong>评价管理</strong>
        <span>查看评价并回复用户，回复会同步用户端</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/merchant/after-sales">
        <strong>售后协同</strong>
        <span>查看本门店售后工单，后台负责最终审核</span>
      </RouterLink>
    </section>

    <div class="ops-layout">
      <section class="panel">
        <div class="panel-heading">
          <h2>最近订单</h2>
          <RouterLink class="button-link" to="/merchant/orders">全部订单</RouterLink>
        </div>
        <div class="data-list">
          <article v-for="order in orders.records" :key="order.id" class="compact-row">
            <div>
              <strong>{{ order.orderNo }} · {{ statusText(order.status) }}</strong>
              <span>{{ order.customerName }} / {{ money(order.payAmount) }} / {{ dateTime(order.createdAt) }}</span>
            </div>
          </article>
          <div v-if="!orders.records?.length" class="empty-state">暂无订单</div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h2>售后动态</h2>
          <RouterLink class="button-link" to="/merchant/after-sales">查看售后</RouterLink>
        </div>
        <div class="data-list">
          <article v-for="ticket in tickets.slice(0, 5)" :key="ticket.id" class="compact-row">
            <div>
              <strong>{{ ticket.orderNo }} · {{ ticketStatusText(ticket.status) }}</strong>
              <span>{{ ticket.reason }}</span>
            </div>
            <span>{{ dateTime(ticket.createdAt) }}</span>
          </article>
          <div v-if="tickets.length === 0" class="empty-state">暂无售后工单</div>
        </div>
      </section>
    </div>
  </section>
</template>
