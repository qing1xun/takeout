<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, type AnyRecord } from '../customer/customerShared'

const reviews = ref<AnyRecord[]>([])
const notice = ref('')
const error = ref('')
const busy = ref(false)
const replyDialog = ref({ open: false, review: null as AnyRecord | null, reply: '' })

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    reviews.value = await api<AnyRecord[]>('MERCHANT', '/merchant/reviews')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function openReply(review: AnyRecord) {
  replyDialog.value = { open: true, review, reply: review.merchantReply || '' }
}

async function submitReply() {
  if (!replyDialog.value.review) {
    return
  }
  await api<AnyRecord>('MERCHANT', `/merchant/reviews/${replyDialog.value.review.id}/reply`, {
    method: 'POST',
    body: { reply: replyDialog.value.reply },
  })
  notice.value = '评价回复已保存，用户端和商家详情页可查看'
  replyDialog.value.open = false
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">商家端 / 评价管理</p>
        <h1>查看用户评价并回复</h1>
      </div>
      <RouterLink class="button-link" to="/merchant/dashboard">返回概览</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>评价列表</h2>
        <span>{{ reviews.length }} 条</span>
      </div>
      <div class="data-list">
        <article v-for="review in reviews" :key="review.id" class="order-card">
          <div class="order-main">
            <div>
              <span class="status-pill">{{ review.score }} 分</span>
              <h3>{{ review.customerName }} · {{ review.orderNo }}</h3>
              <p>{{ review.content || '用户未填写文字评价' }}</p>
            </div>
            <span>{{ dateTime(review.createdAt) }}</span>
          </div>
          <div v-if="review.merchantReply" class="review-box">
            <strong>商家回复</strong>
            <p>{{ review.merchantReply }}</p>
            <small>{{ dateTime(review.repliedAt) }}</small>
          </div>
          <div class="row-actions">
            <button type="button" @click="openReply(review)">{{ review.merchantReply ? '修改回复' : '回复评价' }}</button>
          </div>
        </article>
        <div v-if="reviews.length === 0" class="empty-state">暂无评价</div>
      </div>
    </section>

    <div v-if="replyDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">回复评价</p>
            <h2>{{ replyDialog.review?.orderNo }}</h2>
          </div>
          <button type="button" @click="replyDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>回复内容</span>
          <textarea v-model="replyDialog.reply" placeholder="回复会展示给用户，请填写真实处理说明"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="replyDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitReply">保存回复</button>
        </div>
      </section>
    </div>
  </section>
</template>
