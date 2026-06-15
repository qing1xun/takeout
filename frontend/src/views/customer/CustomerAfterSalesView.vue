<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, ticketStatusText, type AnyRecord } from './customerShared'

const tickets = ref<AnyRecord[]>([])
const error = ref('')
const busy = ref(false)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    tickets.value = await api<AnyRecord[]>('CUSTOMER', '/user/after-sales')
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
        <p class="eyebrow">用户端 / 我的售后</p>
        <h1>退款和异常处理进度</h1>
      </div>
      <RouterLink class="button-link" to="/customer/profile">返回我的</RouterLink>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>售后单</h2>
        <span>{{ tickets.length }} 条</span>
      </div>
      <div class="data-list">
        <article v-for="ticket in tickets" :key="ticket.id" class="order-card">
          <div class="order-main">
            <div>
              <span class="status-pill">{{ ticketStatusText(ticket.status) }}</span>
              <h3>{{ ticket.storeName }} · {{ ticket.orderNo }}</h3>
              <p>{{ ticket.reason }}</p>
            </div>
            <RouterLink class="button-link" :to="`/customer/orders/${ticket.orderId}`">订单详情</RouterLink>
          </div>
          <div class="tracking-line">
            <span>处理结果：{{ ticket.result }}</span>
            <span>创建：{{ dateTime(ticket.createdAt) }}</span>
            <span v-if="ticket.finishedAt">完成：{{ dateTime(ticket.finishedAt) }}</span>
          </div>
        </article>
        <div v-if="tickets.length === 0" class="empty-state">暂无售后记录，可从订单详情发起</div>
      </div>
    </section>
  </section>
</template>
