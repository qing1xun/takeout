<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { getSession } from '@/services/api'
import { api } from '@/services/api'
import { dateTime, ticketStatusText, type AnyRecord } from './customerShared'

const session = getSession('CUSTOMER')
const profile = ref<AnyRecord | null>(null)
const tickets = ref<AnyRecord[]>([])
const error = ref('')
const busy = ref(false)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    const [profileResult, ticketResult] = await Promise.all([
      api<AnyRecord>('CUSTOMER', '/user/profile'),
      api<AnyRecord[]>('CUSTOMER', '/user/after-sales'),
    ])
    profile.value = profileResult
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
        <p class="eyebrow">用户端 / 我的</p>
        <h1>{{ profile?.displayName || session?.displayName || '我的账户' }}</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/customer/home">返回首页</RouterLink>
        <PortalLogoutButton role="CUSTOMER" />
      </div>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section v-if="profile" class="profile-hero panel">
      <div>
        <span class="status-pill">已登录</span>
        <h2>{{ profile.displayName }}</h2>
        <p>账号 {{ profile.username }} / 手机 {{ profile.phoneMasked }}</p>
      </div>
      <div class="profile-metrics">
        <span>地址 {{ profile.addressCount }}</span>
        <span>可用券 {{ profile.availableCouponCount }}</span>
        <span>订单 {{ profile.orderCount }}</span>
        <span>评价 {{ profile.reviewCount }}</span>
        <span>待处理售后 {{ profile.pendingAfterSaleCount }}</span>
      </div>
    </section>

    <section class="profile-grid">
      <RouterLink class="profile-entry" to="/customer/orders">
        <strong>我的订单</strong>
        <span>按状态筛选、支付、确认、评价、售后</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/customer/addresses">
        <strong>收货地址</strong>
        <span>新增、编辑、删除、设默认</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/customer/coupons">
        <strong>我的优惠券</strong>
        <span>未使用、已锁定、已使用、已过期</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/customer/reviews">
        <strong>我的评价</strong>
        <span>查看已评价订单和对应商家</span>
      </RouterLink>
      <RouterLink class="profile-entry" to="/customer/after-sales">
        <strong>我的售后</strong>
        <span>查看退款/售后处理进度</span>
      </RouterLink>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>最近售后</h2>
        <span>{{ tickets.length }} 条</span>
      </div>
      <div class="data-list">
        <article v-for="ticket in tickets.slice(0, 4)" :key="ticket.id" class="compact-row">
          <div>
            <strong>{{ ticket.orderNo }} · {{ ticketStatusText(ticket.status) }}</strong>
            <span>{{ ticket.storeName }} / {{ ticket.reason }}</span>
          </div>
          <span>{{ dateTime(ticket.createdAt) }}</span>
        </article>
        <div v-if="tickets.length === 0" class="empty-state">暂无售后记录</div>
      </div>
    </section>
  </section>
</template>
