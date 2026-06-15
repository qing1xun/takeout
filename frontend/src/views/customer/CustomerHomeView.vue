<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import PortalLogoutButton from '@/components/PortalLogoutButton.vue'
import { api } from '@/services/api'
import { money, type AnyRecord } from './customerShared'

const home = ref<AnyRecord>({ channels: [], banners: [], recommendedStores: [], hotProducts: [] })
const stores = ref<AnyRecord[]>([])
const addresses = ref<AnyRecord[]>([])
const keyword = ref('')
const activeCategory = ref('全部')
const activeSort = ref('default')
const page = ref({ page: 0, size: 10, total: 0, hasMore: false })
const notice = ref('')
const error = ref('')
const busy = ref(false)
const captchaDialog = ref({ open: false, answer: '', message: '', sceneCode: '' })
const pendingBanner = ref<AnyRecord | null>(null)

const categories = computed<string[]>(() => [
  '全部',
  ...new Set<string>((home.value.channels || []).map((item: AnyRecord) => String(item.name))),
])
const defaultAddress = computed(() => addresses.value.find((address) => address.defaultAddress) || addresses.value[0])
const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const pageNumbers = computed(() => Array.from({ length: Math.min(10, totalPages.value) }, (_, index) => index))

watch(
  () => [keyword.value, activeCategory.value, activeSort.value],
  async () => {
    await loadStores(0)
  },
)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    home.value = await api<AnyRecord>(null, '/market/home')
    addresses.value = await api<AnyRecord[]>('CUSTOMER', '/user/addresses')
    await loadStores(0)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function loadStores(pageNo = 0) {
  const params = new URLSearchParams()
  if (keyword.value.trim()) {
    params.set('keyword', keyword.value.trim())
  }
  if (activeCategory.value !== '全部') {
    params.set('category', activeCategory.value)
  }
  if (activeSort.value !== 'default') {
    params.set('sort', activeSort.value)
  }
  params.set('page', String(Math.max(0, pageNo)))
  params.set('size', String(page.value.size))
  const result = await api<AnyRecord>(null, `/stores/page?${params}`)
  page.value = { page: result.page, size: result.size, total: result.total, hasMore: result.hasMore }
  stores.value = result.records
}

async function handleBannerClick(banner: AnyRecord) {
  pendingBanner.value = banner
  await claimBanner(banner, '')
}

async function claimBanner(banner: AnyRecord, captchaAnswer: string) {
  try {
    const claim = await api<AnyRecord>('CUSTOMER', `/marketing/coupons/${banner.sceneCode}/claim`, {
      method: 'POST',
      body: { captchaAnswer },
    })
    captchaDialog.value.open = false
    captchaDialog.value.answer = ''
    notice.value = `${claim.message}，批次 ${claim.batchCode}，剩余券包 ${claim.remainingStock}`
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    if (message.includes('验证码') || message.includes('3+5')) {
      captchaDialog.value = { open: true, answer: '', message, sceneCode: banner.sceneCode }
      return
    }
    error.value = message
  }
}

async function submitCaptcha() {
  if (!pendingBanner.value) {
    return
  }
  await claimBanner(pendingBanner.value, captchaDialog.value.answer)
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端首页</p>
        <h1>首页、门店、活动和附近好店</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/customer/profile">我的</RouterLink>
        <PortalLogoutButton role="CUSTOMER" />
      </div>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section class="market-hero">
      <div class="location-card">
        <span>当前配送地址</span>
        <strong>{{ defaultAddress?.detailMasked || '请维护收货地址' }}</strong>
        <small>共 {{ page.total }} 家可配送门店</small>
        <RouterLink class="button-link" to="/customer/addresses">地址管理</RouterLink>
      </div>
      <div class="search-card">
        <label class="search-box">
          <span>搜索</span>
          <input v-model="keyword" placeholder="搜索商家、商品、品类" @keyup.enter="loadStores(0)" />
          <button type="button" @click="loadStores(0)">查找</button>
        </label>
        <div class="sort-row">
          <button type="button" :class="{ active: activeSort === 'default' }" @click="activeSort = 'default'">综合排序</button>
          <button type="button" :class="{ active: activeSort === 'sales' }" @click="activeSort = 'sales'">销量优先</button>
          <button type="button" :class="{ active: activeSort === 'rating' }" @click="activeSort = 'rating'">评分最高</button>
          <button type="button" :class="{ active: activeSort === 'distance' }" @click="activeSort = 'distance'">距离最近</button>
        </div>
      </div>
    </section>

    <section class="channel-strip">
      <button
        v-for="category in categories"
        :key="category"
        type="button"
        :class="{ active: activeCategory === category }"
        @click="activeCategory = category"
      >
        {{ category }}
      </button>
    </section>

    <section class="banner-grid">
      <button
        v-for="banner in home.banners"
        :key="banner.sceneCode"
        type="button"
        class="promo-banner"
        @click="handleBannerClick(banner)"
      >
        <span>{{ banner.actionText }}</span>
        <strong>{{ banner.title }}</strong>
        <p>{{ banner.subtitle }}</p>
      </button>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>附近好店</h2>
        <span>第 {{ page.page + 1 }} / {{ totalPages }} 页，共 {{ page.total }} 家</span>
      </div>
      <div class="store-card-grid">
        <RouterLink v-for="store in stores" :key="store.id" class="store-card-link" :to="`/customer/stores/${store.id}`">
          <span class="store-logo">{{ store.logoText }}</span>
          <div>
            <strong>{{ store.name }}</strong>
            <small>{{ store.rating }} 分 / 月售 {{ store.monthlySales }} / {{ store.avgDeliveryMinutes }} 分钟</small>
            <p>{{ store.promotions?.[0] || store.notice }}</p>
            <em>起送 {{ money(store.minDeliveryAmount) }} / 配送 {{ money(store.deliveryFee) }}</em>
          </div>
        </RouterLink>
      </div>
      <nav v-if="totalPages > 1" class="pager" aria-label="附近好店分页">
        <button type="button" :disabled="page.page === 0" @click="loadStores(page.page - 1)">‹</button>
        <button
          v-for="pageNo in pageNumbers"
          :key="pageNo"
          type="button"
          :class="{ active: page.page === pageNo }"
          @click="loadStores(pageNo)"
        >
          {{ pageNo + 1 }}
        </button>
        <button type="button" :disabled="page.page >= totalPages - 1" @click="loadStores(page.page + 1)">›</button>
      </nav>
    </section>

    <div v-if="captchaDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">活动风控校验</p>
            <h2>请输入验证码</h2>
          </div>
          <button type="button" @click="captchaDialog.open = false">关闭</button>
        </div>
        <p class="modal-copy">{{ captchaDialog.message }}</p>
        <label class="field">
          <span>计算结果</span>
          <input v-model="captchaDialog.answer" placeholder="请输入 3+5 的结果" @keyup.enter="submitCaptcha" />
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="captchaDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitCaptcha">提交验证并领取</button>
        </div>
      </section>
    </div>
  </section>
</template>
