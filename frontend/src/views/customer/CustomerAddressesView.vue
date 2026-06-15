<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import type { AnyRecord } from './customerShared'

const addresses = ref<AnyRecord[]>([])
const notice = ref('')
const error = ref('')
const busy = ref(false)
const draft = ref({
  id: 0,
  receiver: '',
  phone: '',
  detail: '',
  distanceKm: 2,
  inRange: true,
  defaultAddress: false,
})

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    addresses.value = await api<AnyRecord[]>('CUSTOMER', '/user/addresses')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function resetDraft() {
  draft.value = { id: 0, receiver: '', phone: '', detail: '', distanceKm: 2, inRange: true, defaultAddress: false }
}

function edit(address: AnyRecord) {
  draft.value = {
    id: address.id,
    receiver: address.receiver,
    phone: '',
    detail: address.detailMasked,
    distanceKm: Number(address.distanceKm || 2),
    inRange: Boolean(address.inRange),
    defaultAddress: Boolean(address.defaultAddress),
  }
}

async function save() {
  const body = {
    receiver: draft.value.receiver,
    phone: draft.value.phone || '13800000000',
    detail: draft.value.detail,
    distanceKm: draft.value.distanceKm,
    inRange: draft.value.inRange,
    defaultAddress: draft.value.defaultAddress,
  }
  if (draft.value.id) {
    await api<AnyRecord>('CUSTOMER', `/user/addresses/${draft.value.id}`, { method: 'PATCH', body })
    notice.value = '地址已更新'
  } else {
    await api<AnyRecord>('CUSTOMER', '/user/addresses', { method: 'POST', body })
    notice.value = '地址已新增'
  }
  resetDraft()
  await load()
}

async function remove(address: AnyRecord) {
  addresses.value = await api<AnyRecord[]>('CUSTOMER', `/user/addresses/${address.id}`, { method: 'DELETE' })
  notice.value = '地址已删除'
}

async function setDefault(address: AnyRecord) {
  await api<AnyRecord>('CUSTOMER', `/user/addresses/${address.id}/default`, { method: 'POST' })
  notice.value = '默认地址已更新'
  await load()
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端 / 地址管理</p>
        <h1>收货地址 CRUD 和默认地址</h1>
      </div>
      <RouterLink class="button-link" to="/customer/profile">返回我的</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <div class="detail-layout">
      <section class="panel">
        <div class="panel-heading">
          <h2>地址列表</h2>
          <span>{{ addresses.length }} 条</span>
        </div>
        <div class="data-list">
          <article v-for="address in addresses" :key="address.id" class="compact-row">
            <div>
              <strong>{{ address.receiver }} · {{ address.phoneMasked }}</strong>
              <span>{{ address.detailMasked }} / {{ address.distanceKm }}km / {{ address.inRange ? '可配送' : '超范围' }}</span>
            </div>
            <div class="row-actions">
              <span v-if="address.defaultAddress" class="status-pill">默认</span>
              <button type="button" @click="edit(address)">编辑</button>
              <button v-if="!address.defaultAddress" type="button" @click="setDefault(address)">设默认</button>
              <button type="button" :disabled="addresses.length <= 1" @click="remove(address)">删除</button>
            </div>
          </article>
          <div v-if="addresses.length === 0" class="empty-state">暂无地址，请新增后再下单</div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h2>{{ draft.id ? '编辑地址' : '新增地址' }}</h2>
          <button type="button" @click="resetDraft">清空</button>
        </div>
        <label class="field">
          <span>收货人</span>
          <input v-model="draft.receiver" placeholder="姓名" />
        </label>
        <label class="field">
          <span>手机号</span>
          <input v-model="draft.phone" placeholder="真实手机号会由后端脱敏存储" />
        </label>
        <label class="field">
          <span>详细地址</span>
          <textarea v-model="draft.detail" placeholder="小区、楼栋、门牌号"></textarea>
        </label>
        <label class="field">
          <span>距离门店预估 km</span>
          <input v-model.number="draft.distanceKm" type="number" min="0" step="0.1" />
        </label>
        <label class="field check-field">
          <input v-model="draft.inRange" type="checkbox" />
          <span>在配送范围内</span>
        </label>
        <label class="field check-field">
          <input v-model="draft.defaultAddress" type="checkbox" />
          <span>设为默认地址</span>
        </label>
        <button class="primary" type="button" @click="save">保存地址</button>
      </section>
    </div>
  </section>
</template>
