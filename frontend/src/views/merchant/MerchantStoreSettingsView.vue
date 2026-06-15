<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { money, type AnyRecord } from '../customer/customerShared'

const store = ref<AnyRecord | null>(null)
const notice = ref('')
const error = ref('')
const busy = ref(false)
const draft = ref({
  name: '',
  notice: '',
  statusMessage: '',
  minDeliveryAmount: '',
  deliveryFee: '',
  avgDeliveryMinutes: 30,
  deliveryRangeKm: 5,
  businessHours: '',
  deliveryGuarantee: '',
  tags: '',
  promotions: '',
  couponHints: '',
})

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    store.value = await api<AnyRecord>('MERCHANT', '/merchant/store')
    draft.value = {
      name: store.value.name,
      notice: store.value.notice,
      statusMessage: store.value.statusMessage,
      minDeliveryAmount: String(store.value.minDeliveryAmount),
      deliveryFee: String(store.value.deliveryFee),
      avgDeliveryMinutes: store.value.avgDeliveryMinutes,
      deliveryRangeKm: store.value.deliveryRangeKm,
      businessHours: store.value.businessHours,
      deliveryGuarantee: store.value.deliveryGuarantee,
      tags: (store.value.tags || []).join('，'),
      promotions: (store.value.promotions || []).join('，'),
      couponHints: (store.value.couponHints || []).join('，'),
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function splitList(value: string) {
  return value
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function save() {
  store.value = await api<AnyRecord>('MERCHANT', '/merchant/store', {
    method: 'PATCH',
    body: {
      ...draft.value,
      minDeliveryAmount: draft.value.minDeliveryAmount,
      deliveryFee: draft.value.deliveryFee,
      tags: splitList(draft.value.tags),
      promotions: splitList(draft.value.promotions),
      couponHints: splitList(draft.value.couponHints),
    },
  })
  notice.value = '门店经营配置已保存，后台已记录审计日志'
  await load()
}

async function toggleOpen(open: boolean) {
  store.value = await api<AnyRecord>('MERCHANT', open ? '/merchant/store/open' : '/merchant/store/close', {
    method: 'POST',
  })
  notice.value = open ? '门店已开店营业' : '门店已打烊，用户端将无法继续下单'
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">商家端 / 门店设置</p>
        <h1>营业状态、配送范围和费用配置</h1>
      </div>
      <RouterLink class="button-link" to="/merchant/dashboard">返回概览</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section v-if="store" class="panel detail-summary">
      <div>
        <span class="status-pill">{{ store.open ? '营业中' : '已打烊' }}</span>
        <h2>{{ store.name }}</h2>
        <p>起送 {{ money(store.minDeliveryAmount) }} / 配送 {{ money(store.deliveryFee) }} / {{ store.deliveryRangeKm }}km</p>
      </div>
      <div class="button-row">
        <button v-if="!store.open" type="button" @click="toggleOpen(true)">开店</button>
        <button v-if="store.open" type="button" @click="toggleOpen(false)">打烊</button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>经营信息</h2>
        <span>保存后写入门店表和审计日志</span>
      </div>
      <div class="form-grid">
        <label class="field">
          <span>门店名称</span>
          <input v-model="draft.name" />
        </label>
        <label class="field">
          <span>营业时间</span>
          <input v-model="draft.businessHours" placeholder="09:00-22:00" />
        </label>
        <label class="field">
          <span>配送保障</span>
          <input v-model="draft.deliveryGuarantee" />
        </label>
        <label class="field field-wide">
          <span>门店公告</span>
          <textarea v-model="draft.notice"></textarea>
        </label>
        <label class="field field-wide">
          <span>履约说明</span>
          <textarea v-model="draft.statusMessage"></textarea>
        </label>
        <label class="field">
          <span>起送价</span>
          <input v-model="draft.minDeliveryAmount" />
        </label>
        <label class="field">
          <span>配送费</span>
          <input v-model="draft.deliveryFee" />
        </label>
        <label class="field">
          <span>预计送达分钟</span>
          <input v-model.number="draft.avgDeliveryMinutes" type="number" min="10" max="120" />
        </label>
        <label class="field">
          <span>配送范围 km</span>
          <input v-model.number="draft.deliveryRangeKm" type="number" min="0.5" max="30" step="0.1" />
        </label>
        <label class="field">
          <span>门店标签</span>
          <input v-model="draft.tags" placeholder="逗号分隔" />
        </label>
        <label class="field">
          <span>活动文案</span>
          <input v-model="draft.promotions" placeholder="逗号分隔" />
        </label>
        <label class="field">
          <span>券提示</span>
          <input v-model="draft.couponHints" placeholder="逗号分隔" />
        </label>
        <button class="primary" type="button" @click="save">保存门店配置</button>
      </div>
    </section>
  </section>
</template>
