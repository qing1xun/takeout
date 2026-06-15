<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { api } from '@/services/api'
import type { AnyRecord } from '../customer/customerShared'

const keyword = ref('')
const accounts = ref<AnyRecord[]>([])
const page = ref({ page: 0, size: 20, total: 0, hasMore: false })
const busy = ref(false)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const pageNumbers = computed(() => Array.from({ length: Math.min(10, totalPages.value) }, (_, index) => index))

watch(keyword, async () => {
  await load(0)
})

load(0)

async function load(pageNo = 0) {
  busy.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(Math.max(0, pageNo)))
    params.set('size', String(page.value.size))
    if (keyword.value.trim()) {
      params.set('keyword', keyword.value.trim())
    }
    const result = await api<AnyRecord>('ADMIN', `/merchant/accounts/page?${params}`)
    accounts.value = result.records
    page.value = { page: result.page, size: result.size, total: result.total, hasMore: result.hasMore }
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
        <p class="eyebrow">后台端 / 商家账号映射</p>
        <h1>168 家门店与登录账号对应关系</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/admin">后台首页</RouterLink>
        <RouterLink class="button-link" to="/admin/onboarding-applications">入驻审核</RouterLink>
        <RouterLink class="button-link" to="/admin/coupons">优惠券治理</RouterLink>
        <PortalLogoutButton role="ADMIN" />
      </div>
    </header>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="panel">
      <div class="panel-heading">
        <h2>账号映射</h2>
        <span>第 {{ page.page + 1 }} / {{ totalPages }} 页，共 {{ page.total }} 家</span>
      </div>
      <label class="field account-search">
        <span>搜索账号、门店、品类、区域</span>
        <input v-model="keyword" placeholder="例如 merchant_chuanwei、川味厨房、唐镇" />
      </label>
      <p class="account-note">该映射仅管理员可见；页面不展示密码，商家账号密码应由后台重置流程处理。</p>
      <div class="data-list">
        <article v-for="account in accounts" :key="account.id" class="compact-row">
          <div>
            <strong>{{ account.username }} → {{ account.storeName }}</strong>
            <span>{{ account.displayName }} / {{ account.category }} / {{ account.area }} / 门店 {{ account.storeId }}</span>
          </div>
          <span>{{ account.open ? '营业中' : '已打烊' }} / 月售 {{ account.monthlySales }}</span>
        </article>
        <div v-if="accounts.length === 0" class="empty-state">没有匹配的商家账号</div>
      </div>
      <nav v-if="totalPages > 1" class="pager" aria-label="商家账号分页">
        <button type="button" :disabled="page.page === 0" @click="load(page.page - 1)">‹</button>
        <button
          v-for="pageNo in pageNumbers"
          :key="pageNo"
          type="button"
          :class="{ active: page.page === pageNo }"
          @click="load(pageNo)"
        >
          {{ pageNo + 1 }}
        </button>
        <button type="button" :disabled="page.page >= totalPages - 1" @click="load(page.page + 1)">›</button>
      </nav>
    </section>
  </section>
</template>
