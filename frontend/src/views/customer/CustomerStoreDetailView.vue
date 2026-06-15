<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '@/services/api'
import { dateTime, money, type AnyRecord } from './customerShared'

const route = useRoute()
const storeId = Number(route.params.id)
const store = ref<AnyRecord | null>(null)
const products = ref<AnyRecord[]>([])
const reviews = ref<AnyRecord[]>([])
const cart = ref<AnyRecord[]>([])
const activeCategory = ref('全部')
const notice = ref('')
const error = ref('')
const busy = ref(false)

const productCategories = computed(() => [
  '全部',
  ...new Set<string>(products.value.map((product) => String(product.category)).filter(Boolean)),
])
const visibleProducts = computed(() =>
  activeCategory.value === '全部'
    ? products.value
    : products.value.filter((product) => product.category === activeCategory.value),
)
const cartCount = computed(() => cart.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0))

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    store.value = await api<AnyRecord>(null, `/stores/${storeId}`)
    products.value = await api<AnyRecord[]>(null, `/stores/${storeId}/products`)
    reviews.value = await api<AnyRecord[]>(null, `/stores/${storeId}/reviews`)
    cart.value = await api<AnyRecord[]>('CUSTOMER', '/cart/items')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function clearCart() {
  const ids = cart.value.map((item) => item.productId)
  for (const productId of ids) {
    cart.value = await api<AnyRecord[]>('CUSTOMER', `/cart/items/${productId}`, { method: 'DELETE' })
  }
}

async function addToCart(product: AnyRecord) {
  if (cart.value.some((item) => item.storeId !== product.storeId)) {
    await clearCart()
    notice.value = '已切换门店购物车'
  }
  cart.value = await api<AnyRecord[]>('CUSTOMER', '/cart/items', {
    method: 'POST',
    body: { productId: product.id, quantity: 1 },
  })
  notice.value = `${product.name} 已加入购物车`
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">商家详情</p>
        <h1>{{ store?.name || '门店加载中' }}</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/customer/home">返回首页</RouterLink>
        <RouterLink class="button-link primary-link" to="/customer/checkout">购物车 {{ cartCount }}</RouterLink>
      </div>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <section v-if="store" class="store-hero-card page-hero-card">
      <div class="store-logo xl">{{ store.logoText }}</div>
      <div>
        <div class="store-title-line">
          <h2>{{ store.name }}</h2>
          <span>{{ store.open ? '营业中' : '已打烊' }}</span>
          <span>{{ store.category }}</span>
        </div>
        <p>{{ store.notice }}</p>
        <div class="store-metrics">
          <span>{{ store.rating }} 分</span>
          <span>月售 {{ store.monthlySales }}</span>
          <span>{{ store.distanceKm }}km</span>
          <span>营业 {{ store.businessHours }}</span>
          <span>起送 {{ money(store.minDeliveryAmount) }}</span>
          <span>配送 {{ money(store.deliveryFee) }}</span>
        </div>
        <div class="coupon-line">
          <span v-for="promo in store.promotions" :key="promo">{{ promo }}</span>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>商品菜单</h2>
        <span>{{ visibleProducts.length }} 个商品</span>
      </div>
      <nav class="menu-tabs horizontal-tabs">
        <button
          v-for="category in productCategories"
          :key="category"
          type="button"
          :class="{ active: activeCategory === category }"
          @click="activeCategory = category"
        >
          {{ category }}
        </button>
      </nav>
      <div class="menu-grid">
        <article v-for="product in visibleProducts" :key="product.id" class="food-card">
          <div class="food-art" :data-tone="product.imageTone">
            <span>{{ product.name.slice(0, 2) }}</span>
          </div>
          <div class="food-body">
            <div class="food-title">
              <h3>{{ product.name }}</h3>
              <span v-if="product.discountLabel">{{ product.discountLabel }}</span>
            </div>
            <p>{{ product.description }}</p>
            <small>月售 {{ product.monthlySales }} / 库存 {{ product.stock }}</small>
            <div class="price-line">
              <strong>{{ money(product.price) }}</strong>
              <del v-if="Number(product.originalPrice) > Number(product.price)">{{ money(product.originalPrice) }}</del>
              <button type="button" :disabled="!store?.open || !product.onSale || product.stock < 1" @click="addToCart(product)">
                加购
              </button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>门店评价</h2>
        <span>{{ reviews.length }} 条</span>
      </div>
      <div class="data-list">
        <article v-for="review in reviews" :key="review.id" class="compact-row">
          <div>
            <strong>{{ review.customerName }} · {{ review.score }} 分</strong>
            <span>{{ review.content || '用户未填写文字评价' }}</span>
          </div>
          <span>{{ dateTime(review.createdAt) }}</span>
        </article>
        <div v-if="reviews.length === 0" class="empty-state">暂无评价</div>
      </div>
    </section>
  </section>
</template>
