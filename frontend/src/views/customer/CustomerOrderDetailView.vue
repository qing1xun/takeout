<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, money, orderCanRefund, statusText, type AnyRecord } from './customerShared'

const route = useRoute()
const orderId = Number(route.params.id)
const order = ref<AnyRecord | null>(null)
const notice = ref('')
const error = ref('')
const busy = ref(false)
const refundReason = ref('')
const reviewScore = ref(5)
const reviewContent = ref('')

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    order.value = await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function pay() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/pay`, { method: 'POST', body: {} })
  notice.value = '支付成功'
  await load()
}

async function confirm() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/confirm`, { method: 'POST' })
  notice.value = '已确认收货'
  await load()
}

async function refund() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/refund`, {
    method: 'POST',
    body: { reason: refundReason.value || '用户从订单详情发起售后' },
  })
  refundReason.value = ''
  notice.value = '售后状态已更新'
  await load()
}

async function review() {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${orderId}/review`, {
    method: 'POST',
    body: { score: reviewScore.value, content: reviewContent.value },
  })
  reviewContent.value = ''
  notice.value = '评价已提交'
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端 / 订单详情</p>
        <h1>{{ order?.orderNo || '订单详情' }}</h1>
      </div>
      <RouterLink class="button-link" to="/customer/orders">返回订单列表</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <template v-if="order">
      <section class="panel detail-summary">
        <div>
          <span class="status-pill">{{ statusText(order.status) }}</span>
          <h2>{{ order.storeName }}</h2>
          <p>{{ order.addressSnapshot?.receiver }} / {{ order.addressSnapshot?.phoneMasked }}</p>
          <p>{{ order.addressSnapshot?.detailMasked }} / {{ order.addressSnapshot?.distanceKm }}km</p>
        </div>
        <strong>{{ money(order.payAmount) }}</strong>
      </section>

      <div class="detail-layout">
        <section class="panel">
          <div class="panel-heading">
            <h2>商品明细</h2>
            <span>{{ order.items?.length || 0 }} 件</span>
          </div>
          <div class="data-list">
            <article v-for="item in order.items" :key="item.productId" class="compact-row">
              <div>
                <strong>{{ item.productName }}</strong>
                <span>{{ money(item.price) }} x {{ item.quantity }}</span>
              </div>
              <strong>{{ money(item.subtotal) }}</strong>
            </article>
          </div>
          <div class="amount-list">
            <span>商品金额 <strong>{{ money(order.itemAmount) }}</strong></span>
            <span>配送费 <strong>{{ money(order.deliveryFee) }}</strong></span>
            <span>优惠抵扣 <strong>{{ money(order.discountAmount) }}</strong></span>
            <span>实付金额 <strong>{{ money(order.payAmount) }}</strong></span>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>订单状态日志</h2>
            <span>{{ order.statusRecords?.length || 0 }} 条</span>
          </div>
          <div class="timeline-list">
            <article v-for="record in order.statusRecords" :key="record.id">
              <strong>{{ statusText(record.afterStatus) }}</strong>
              <span>{{ record.operator }} / {{ record.reason }}</span>
              <small>{{ dateTime(record.createdAt) }}</small>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>履约进度</h2>
            <span>预计 {{ order.estimatedDeliveryMinutes }} 分钟</span>
          </div>
          <div class="timeline-list">
            <article v-for="step in order.fulfillmentSteps" :key="step.id">
              <strong>{{ step.title }}</strong>
              <span>{{ step.operator }} / {{ step.detail }}</span>
              <small>{{ dateTime(step.createdAt) }}</small>
            </article>
            <div v-if="!order.fulfillmentSteps?.length" class="empty-state">商家接单后会生成履约进度</div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>可处理动作</h2>
            <span>{{ statusText(order.status) }}</span>
          </div>
          <div class="button-row">
            <button v-if="order.status === 'WAIT_PAY'" type="button" @click="pay">支付订单</button>
            <button v-if="order.status === 'DELIVERED'" type="button" @click="confirm">确认收货</button>
            <RouterLink class="button-link" :to="`/customer/stores/${order.storeId}`">查看商家</RouterLink>
          </div>
          <label v-if="orderCanRefund(order.status)" class="field">
            <span>{{ order.status === 'WAIT_PAY' ? '取消原因' : '售后原因' }}</span>
            <textarea v-model="refundReason" placeholder="请填写原因，提交后会进入订单状态日志或售后单"></textarea>
          </label>
          <button v-if="orderCanRefund(order.status)" type="button" @click="refund">
            {{ order.status === 'WAIT_PAY' ? '取消订单' : '提交售后' }}
          </button>

          <div v-if="order.review" class="review-box">
            <strong>我的评价：{{ order.review.score }} 分</strong>
            <p>{{ order.review.content || '未填写文字评价' }}</p>
          </div>
          <template v-else-if="order.status === 'COMPLETED'">
            <label class="field">
              <span>评分</span>
              <select v-model.number="reviewScore">
                <option v-for="score in [5, 4, 3, 2, 1]" :key="score" :value="score">{{ score }} 分</option>
              </select>
            </label>
            <label class="field">
              <span>评价内容</span>
              <textarea v-model="reviewContent" placeholder="确认收货后才能评价，评价会同步到商家详情"></textarea>
            </label>
            <button type="button" @click="review">提交评价</button>
          </template>
        </section>
      </div>
    </template>
  </section>
</template>
