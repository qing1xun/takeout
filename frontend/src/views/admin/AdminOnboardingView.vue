<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { api } from '@/services/api'
import { dateTime, type AnyRecord } from '../customer/customerShared'

const applications = ref<AnyRecord[]>([])
const status = ref('')
const notice = ref('')
const error = ref('')
const busy = ref(false)
const reviewDialog = ref({
  open: false,
  action: 'approve' as 'approve' | 'reject',
  application: null as AnyRecord | null,
  username: '',
  initialPassword: '123456',
  result: '',
})

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    const params = status.value ? `?status=${encodeURIComponent(status.value)}` : ''
    applications.value = await api<AnyRecord[]>('ADMIN', `/admin/onboarding-applications${params}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function openReview(application: AnyRecord, action: 'approve' | 'reject') {
  reviewDialog.value = {
    open: true,
    action,
    application,
    username: application.preferredUsername || '',
    initialPassword: '123456',
    result: action === 'approve' ? '资料齐全，审核通过，账号已创建。' : '资料不足，暂不通过，请补充后重新提交。',
  }
}

async function submitReview() {
  const application = reviewDialog.value.application
  if (!application) {
    return
  }
  const path = `/admin/onboarding-applications/${application.id}/${reviewDialog.value.action}`
  await api<AnyRecord>('ADMIN', path, {
    method: 'POST',
    body: {
      username: reviewDialog.value.username || undefined,
      initialPassword: reviewDialog.value.initialPassword,
      result: reviewDialog.value.result,
    },
  })
  notice.value = `申请 ${application.id} 已${reviewDialog.value.action === 'approve' ? '通过' : '驳回'}`
  reviewDialog.value.open = false
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">后台端 / 入驻审核</p>
        <h1>商家和骑手申请审核</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/admin">后台首页</RouterLink>
        <RouterLink class="button-link" to="/admin/merchant-accounts">商家账号映射</RouterLink>
        <RouterLink class="button-link" to="/admin/coupons">优惠券治理</RouterLink>
        <PortalLogoutButton role="ADMIN" />
      </div>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>申请列表</h2>
        <div class="button-row">
          <button type="button" :class="{ active: status === '' }" @click="status = ''; load()">全部</button>
          <button type="button" :class="{ active: status === 'PENDING' }" @click="status = 'PENDING'; load()">待审核</button>
          <button type="button" :class="{ active: status === 'APPROVED' }" @click="status = 'APPROVED'; load()">已通过</button>
          <button type="button" :class="{ active: status === 'REJECTED' }" @click="status = 'REJECTED'; load()">已驳回</button>
        </div>
      </div>
      <div class="data-list">
        <article v-for="application in applications" :key="application.id" class="order-card">
          <div class="order-main">
            <div>
              <span class="status-pill">{{ application.status }}</span>
              <h3>{{ application.role === 'MERCHANT' ? application.storeName : application.applicantName }}</h3>
              <p>
                {{ application.role }} / {{ application.phoneMasked }} / {{ application.category || '骑手' }} /
                {{ application.address || '无门店地址' }}
              </p>
              <p>期望账号：{{ application.preferredUsername || '系统生成' }} / {{ application.reason }}</p>
            </div>
            <span>{{ dateTime(application.createdAt) }}</span>
          </div>
          <div v-if="application.createdUsername" class="tracking-line">
            <span>创建账号：{{ application.createdUsername }}</span>
            <span v-if="application.createdStoreName">绑定门店：{{ application.createdStoreName }}</span>
            <span>{{ application.result }}</span>
          </div>
          <div v-if="application.status === 'PENDING'" class="row-actions">
            <button type="button" @click="openReview(application, 'approve')">通过并创建账号</button>
            <button type="button" @click="openReview(application, 'reject')">驳回</button>
          </div>
        </article>
        <div v-if="applications.length === 0" class="empty-state">暂无入驻申请</div>
      </div>
    </section>

    <div v-if="reviewDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">审核入驻</p>
            <h2>{{ reviewDialog.application?.storeName || reviewDialog.application?.applicantName }}</h2>
          </div>
          <button type="button" @click="reviewDialog.open = false">关闭</button>
        </div>
        <template v-if="reviewDialog.action === 'approve'">
          <label class="field">
            <span>创建账号</span>
            <input v-model="reviewDialog.username" placeholder="为空则系统生成" />
          </label>
          <label class="field">
            <span>初始密码</span>
            <input v-model="reviewDialog.initialPassword" type="password" />
          </label>
        </template>
        <label class="field">
          <span>审核意见</span>
          <textarea v-model="reviewDialog.result"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="reviewDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitReview">提交审核</button>
        </div>
      </section>
    </div>
  </section>
</template>
