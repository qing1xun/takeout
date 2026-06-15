<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, type AnyRecord } from './customerShared'

const reviews = ref<AnyRecord[]>([])
const error = ref('')
const busy = ref(false)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    reviews.value = await api<AnyRecord[]>('CUSTOMER', '/user/reviews')
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
        <p class="eyebrow">用户端 / 我的评价</p>
        <h1>已评价订单和商家回看</h1>
      </div>
      <RouterLink class="button-link" to="/customer/profile">返回我的</RouterLink>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>评价记录</h2>
        <span>{{ reviews.length }} 条</span>
      </div>
      <div class="data-list">
        <article v-for="review in reviews" :key="review.id" class="compact-row">
          <div>
            <strong>{{ review.storeName }} · {{ review.score }} 分</strong>
            <span>{{ review.content || '未填写文字评价' }}</span>
            <small>{{ review.orderNo }} / {{ dateTime(review.createdAt) }}</small>
          </div>
          <div class="row-actions">
            <RouterLink class="button-link" :to="`/customer/orders/${review.orderId}`">订单</RouterLink>
            <RouterLink class="button-link" :to="`/customer/stores/${review.storeId}`">商家</RouterLink>
          </div>
        </article>
        <div v-if="reviews.length === 0" class="empty-state">暂无评价记录，订单完成后可评价</div>
      </div>
    </section>
  </section>
</template>
