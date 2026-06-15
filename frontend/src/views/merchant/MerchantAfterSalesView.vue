<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, ticketStatusText, type AnyRecord } from '../customer/customerShared'

const tickets = ref<AnyRecord[]>([])
const error = ref('')
const busy = ref(false)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    tickets.value = await api<AnyRecord[]>('MERCHANT', '/merchant/after-sales')
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
        <p class="eyebrow">商家端 / 售后协同</p>
        <h1>查看本门店退款和售后工单</h1>
      </div>
      <RouterLink class="button-link" to="/merchant/dashboard">返回概览</RouterLink>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>售后工单</h2>
        <span>{{ tickets.length }} 件</span>
      </div>
      <div class="data-list">
        <article v-for="ticket in tickets" :key="ticket.id" class="order-card">
          <div class="order-main">
            <div>
              <span class="status-pill">{{ ticketStatusText(ticket.status) }}</span>
              <h3>{{ ticket.orderNo }} · 工单 {{ ticket.id }}</h3>
              <p>{{ ticket.reason }}</p>
            </div>
            <span>{{ dateTime(ticket.createdAt) }}</span>
          </div>
          <div class="tracking-line">
            <span>后台处理：{{ ticket.result }}</span>
            <span v-if="ticket.finishedAt">完成时间：{{ dateTime(ticket.finishedAt) }}</span>
            <span v-else>等待后台治理端处理</span>
          </div>
        </article>
        <div v-if="tickets.length === 0" class="empty-state">暂无售后工单</div>
      </div>
    </section>
  </section>
</template>
