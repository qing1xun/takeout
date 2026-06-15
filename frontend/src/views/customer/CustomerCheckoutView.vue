<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '@/services/api'
import { couponStatusText, money, type AnyRecord } from './customerShared'

const router = useRouter()
const cart = ref<AnyRecord[]>([])
const addresses = ref<AnyRecord[]>([])
const coupons = ref<AnyRecord[]>([])
const selectedAddressId = ref<number | null>(null)
const selectedCouponCode = ref('')
const notice = ref('')
const error = ref('')
const busy = ref(false)
const submitting = ref(false)

const storeIds = computed(() => [...new Set(cart.value.map((item) => item.storeId))])
const storeId = computed(() => storeIds.value[0] ?? null)
const itemAmount = computed(() => cart.value.reduce((sum, item) => sum + Number(item.subtotal || 0), 0))
const availableCoupons = computed(() => coupons.value.filter((coupon) => coupon.status === 'UNUSED'))
const selectedCoupon = computed(() =>
  coupons.value.find((coupon) => coupon.couponCode === selectedCouponCode.value) || null,
)
const canSubmit = computed(
  () => cart.value.length > 0 && storeIds.value.length === 1 && selectedAddressId.value !== null && !submitting.value,
)

load()

async function load() {
  busy.value = true
  error.value = ''
  try {
    const [cartResult, addressResult, couponResult] = await Promise.all([
      api<AnyRecord[]>('CUSTOMER', '/cart/items'),
      api<AnyRecord[]>('CUSTOMER', '/user/addresses'),
      api<AnyRecord[]>('CUSTOMER', '/user/coupons'),
    ])
    cart.value = cartResult
    addresses.value = addressResult
    coupons.value = couponResult
    selectedAddressId.value =
      addresses.value.find((address) => address.defaultAddress)?.id ?? addresses.value[0]?.id ?? null
    selectedCouponCode.value = availableCoupons.value[0]?.couponCode || ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

async function removeItem(productId: number) {
  cart.value = await api<AnyRecord[]>('CUSTOMER', `/cart/items/${productId}`, { method: 'DELETE' })
  notice.value = '购物车已更新'
}

async function submitOrder() {
  if (!canSubmit.value) {
    error.value = '请确认购物车、收货地址和门店商品'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const token = await api<AnyRecord>('CUSTOMER', '/orders/idempotency-token')
    const order = await api<AnyRecord>('CUSTOMER', '/orders', {
      method: 'POST',
      body: {
        storeId: storeId.value,
        addressId: selectedAddressId.value,
        couponCode: selectedCouponCode.value || undefined,
        idempotencyToken: token.token,
        items: cart.value.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      },
    })
    await router.push(`/customer/orders/${order.id}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">用户端 / 结算页</p>
        <h1>确认商品、地址和优惠</h1>
      </div>
      <div class="button-row">
        <RouterLink class="button-link" to="/customer/home">继续选购</RouterLink>
        <RouterLink class="button-link" to="/customer/orders">我的订单</RouterLink>
      </div>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <div class="checkout-layout">
      <section class="panel">
        <div class="panel-heading">
          <h2>购物车</h2>
          <span>{{ cart.length }} 件商品</span>
        </div>
        <div class="data-list">
          <article v-for="item in cart" :key="item.productId" class="compact-row">
            <div>
              <strong>{{ item.productName }}</strong>
              <span>{{ money(item.price) }} x {{ item.quantity }}</span>
            </div>
            <div class="row-actions">
              <strong>{{ money(item.subtotal) }}</strong>
              <button type="button" @click="removeItem(item.productId)">移除</button>
            </div>
          </article>
          <div v-if="cart.length === 0" class="empty-state">购物车为空，请先进入商家详情页加购商品</div>
          <div v-if="storeIds.length > 1" class="error">一个订单只能包含同一门店商品，请先移除其他门店商品。</div>
        </div>
      </section>

      <aside class="checkout-panel">
        <h2>提交订单</h2>
        <label class="field">
          <span>收货地址</span>
          <select v-model.number="selectedAddressId">
            <option v-for="address in addresses" :key="address.id" :value="address.id">
              {{ address.receiver }} / {{ address.detailMasked }} / {{ address.inRange ? '可配送' : '超范围' }}
            </option>
          </select>
        </label>
        <RouterLink class="button-link block-link" to="/customer/addresses">管理地址</RouterLink>

        <label class="field">
          <span>可用优惠券</span>
          <select v-model="selectedCouponCode">
            <option value="">不使用优惠券</option>
            <option v-for="coupon in availableCoupons" :key="coupon.couponCode" :value="coupon.couponCode">
              {{ coupon.title }} / {{ coupon.discountText }} / {{ coupon.thresholdText }} / {{ coupon.scope }}
            </option>
          </select>
        </label>
        <p v-if="selectedCoupon" class="helper-copy">
          已选择 {{ selectedCoupon.couponCode }}（{{ selectedCoupon.batchCode }}），下单时服务端会重新校验门槛并锁定该券实例。
        </p>
        <div v-if="coupons.length" class="mini-list">
          <span v-for="coupon in coupons" :key="coupon.couponCode">
            {{ coupon.title }} · {{ couponStatusText(coupon.status) }}
          </span>
        </div>

        <div class="checkout-total">
          <span>商品小计</span>
          <strong>{{ money(itemAmount) }}</strong>
        </div>
        <button class="primary" type="button" :disabled="!canSubmit" @click="submitOrder">
          {{ submitting ? '提交中...' : '提交订单' }}
        </button>
      </aside>
    </div>
  </section>
</template>
