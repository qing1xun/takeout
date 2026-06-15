<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, getSession, logout, roleLogin, type Role, type Session } from '@/services/api'

type AnyRecord = Record<string, any>

interface PageState {
  page: number
  size: number
  total: number
  hasMore?: boolean
}

const route = useRoute()
const router = useRouter()
const role = computed<Role>(() => (route.meta.role as Role) || 'CUSTOMER')
const session = ref<Session | null>(null)
const busy = ref(false)
const notice = ref('')
const error = ref('')

const home = ref<AnyRecord>({ channels: [], banners: [], recommendedStores: [], hotProducts: [] })
const stores = ref<AnyRecord[]>([])
const products = ref<AnyRecord[]>([])
const selectedStore = ref<AnyRecord | null>(null)
const selectedStoreId = ref<number>(101)
const addresses = ref<AnyRecord[]>([])
const selectedAddressId = ref<number>(501)
const cart = ref<AnyRecord[]>([])
const orders = ref<AnyRecord[]>([])
const keyword = ref('')
const activeCategory = ref('全部')
const activeSort = ref('default')
const activeProductCategory = ref('全部')
const couponCode = ref('')
const storePage = ref<PageState>({ page: 0, size: 10, total: 0, hasMore: false })
const customerOrderPage = ref<PageState>({ page: 0, size: 4, total: 0 })
const addressDialog = ref({
  open: false,
  mode: 'create' as 'create' | 'edit',
  address: null as AnyRecord | null,
  receiver: '',
  phone: '',
  detail: '',
  distanceKm: 2,
  inRange: true,
  defaultAddress: false,
})

const merchantStore = ref<AnyRecord | null>(null)
const merchantProducts = ref<AnyRecord[]>([])
const merchantOrders = ref<AnyRecord[]>([])
const pendingOrders = ref<AnyRecord[]>([])
const merchantProductPage = ref<PageState>({ page: 0, size: 10, total: 0, hasMore: false })
const merchantOrderPage = ref<PageState>({ page: 0, size: 6, total: 0, hasMore: false })
const pendingOrderPage = ref<PageState>({ page: 0, size: 4, total: 0 })
const productDraft = ref({
  name: '番茄牛腩饭',
  description: '番茄慢炖牛腩，配米饭和时蔬',
  price: '28.00',
  stock: 20,
  onSale: true,
  category: '商家新品',
  imageTone: 'rice',
  discountLabel: '新品',
  originalPrice: '32.00',
})
const storeDraft = ref({
  name: '',
  notice: '',
  open: true,
  minDeliveryAmount: '20.00',
  deliveryFee: '4.00',
  avgDeliveryMinutes: 30,
  deliveryRangeKm: 6,
  businessHours: '09:00-22:00',
  deliveryGuarantee: '准时宝',
  statusMessage: '',
  tagsText: '',
  promotionsText: '',
  couponHintsText: '',
})

const availableRiderTasks = ref<AnyRecord[]>([])
const riderTasks = ref<AnyRecord[]>([])
const availableRiderPage = ref<PageState>({ page: 0, size: 4, total: 0 })
const riderTaskPage = ref<PageState>({ page: 0, size: 6, total: 0 })
const adminOrders = ref<AnyRecord[]>([])
const tickets = ref<AnyRecord[]>([])
const riskRecords = ref<AnyRecord[]>([])
const auditLogs = ref<AnyRecord[]>([])
const adminOrderPage = ref<PageState>({ page: 0, size: 8, total: 0 })
const ticketPage = ref<PageState>({ page: 0, size: 6, total: 0 })
const riskPage = ref<PageState>({ page: 0, size: 8, total: 0 })
const auditPage = ref<PageState>({ page: 0, size: 8, total: 0 })
const snapshot = ref<AnyRecord | null>(null)

const captchaDialog = ref({ open: false, answer: '', message: '', sceneCode: '' })
const pendingBanner = ref<AnyRecord | null>(null)
const refundDialog = ref({
  open: false,
  order: null as AnyRecord | null,
  reason: '配送时间过长',
  detail: '预计送达时间已超出可接受范围，请平台介入退款。',
})
const reviewDialog = ref({
  open: false,
  order: null as AnyRecord | null,
  score: 5,
  content: '味道不错，配送及时，包装完整。',
})
const rejectOrderDialog = ref({
  open: false,
  order: null as AnyRecord | null,
  reason: '高峰期爆单，预计无法按时出餐',
  detail: '建议用户重新选择附近可接单门店。',
})
const riderExceptionDialog = ref({
  open: false,
  task: null as AnyRecord | null,
  type: '联系不上用户',
  detail: '电话多次无人接听，已在收货地址附近等待超过 10 分钟。',
  evidenceNo: `EV-${Date.now()}`,
})
const ticketDialog = ref({
  open: false,
  ticket: null as AnyRecord | null,
  action: 'approve' as 'approve' | 'reject',
  result: '审核通过，退款成功，系统已记录处理原因。',
})

const roleTitle = computed(() => {
  const names: Record<Role, string> = {
    CUSTOMER: '用户端',
    MERCHANT: '商家端',
    RIDER: '骑手端',
    ADMIN: '后台端',
  }
  return names[role.value]
})

const roleSummary = computed(() => {
  const summary: Record<Role, string> = {
    CUSTOMER: '外卖首页、门店详情、活动券、购物车、结算和订单追踪',
    MERCHANT: '门店经营、商品供给、订单漏斗、出餐履约',
    RIDER: '实时任务、取送状态、异常上报、履约绩效',
    ADMIN: '平台订单、售后审核、风险治理、审计追踪',
  }
  return summary[role.value]
})

const categories = computed<string[]>(() => [
  '全部',
  ...new Set<string>((home.value.channels || []).map((item: AnyRecord) => String(item.name))),
])
const productCategories = computed(() => [
  '全部',
  ...new Set<string>(products.value.map((product) => String(product.category)).filter(Boolean)),
])
const visibleProducts = computed(() => {
  if (activeProductCategory.value === '全部') {
    return products.value
  }
  return products.value.filter((product) => product.category === activeProductCategory.value)
})
const cartTotal = computed(() =>
  cart.value.reduce((sum, item) => sum + Number(item.subtotal || Number(item.price || 0) * Number(item.quantity || 0)), 0),
)
const cartCount = computed(() => cart.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0))
const latestOrder = computed(() => orders.value[0] || null)
const activeStorePromotions = computed(() => selectedStore.value?.promotions || [])
const pagedCustomerOrders = computed(() => pageSlice(orders.value, customerOrderPage.value))
const pendingOrderPager = computed(() => pageInfo(pendingOrders.value.length, pendingOrderPage.value))
const pagedPendingOrders = computed(() => pageSlice(pendingOrders.value, pendingOrderPager.value))
const availableRiderPager = computed(() => pageInfo(availableRiderTasks.value.length, availableRiderPage.value))
const pagedAvailableRiderTasks = computed(() => pageSlice(availableRiderTasks.value, availableRiderPager.value))
const riderTaskPager = computed(() => pageInfo(riderTasks.value.length, riderTaskPage.value))
const pagedRiderTasks = computed(() => pageSlice(riderTasks.value, riderTaskPager.value))
const adminOrderPager = computed(() => pageInfo(adminOrders.value.length, adminOrderPage.value))
const pagedAdminOrders = computed(() => pageSlice(adminOrders.value, adminOrderPager.value))
const ticketPager = computed(() => pageInfo(tickets.value.length, ticketPage.value))
const pagedTickets = computed(() => pageSlice(tickets.value, ticketPager.value))
const riskPager = computed(() => pageInfo(riskRecords.value.length, riskPage.value))
const pagedRiskRecords = computed(() => pageSlice(riskRecords.value, riskPager.value))
const auditPager = computed(() => pageInfo(auditLogs.value.length, auditPage.value))
const pagedAuditLogs = computed(() => pageSlice(auditLogs.value, auditPager.value))

const kpis = computed(() => {
  if (role.value === 'ADMIN') {
    return [
      { label: '平台订单', value: adminOrderPager.value.total },
      { label: '待审工单', value: tickets.value.filter((ticket) => ticket.status === 'PENDING').length },
      { label: '风控事件', value: riskPager.value.total },
      { label: '审计留痕', value: auditPager.value.total },
    ]
  }
  if (role.value === 'MERCHANT') {
    return [
      { label: '商品总数', value: merchantProductPage.value.total },
      { label: '待接单', value: pendingOrders.value.length },
      { label: '订单总数', value: merchantOrderPage.value.total },
      { label: '当前页库存', value: merchantProducts.value.reduce((sum, product) => sum + Number(product.stock || 0), 0) },
    ]
  }
  if (role.value === 'RIDER') {
    return [
      { label: '可接任务', value: availableRiderTasks.value.length },
      { label: '待到店', value: riderTasks.value.filter((task) => task.status === 'ACCEPTED').length },
      { label: '配送中', value: riderTasks.value.filter((task) => task.status === 'DELIVERING').length },
      { label: '异常', value: riderTasks.value.filter((task) => task.exceptionReason).length },
    ]
  }
  return [
    { label: '附近门店', value: storePage.value.total || stores.value.length },
    { label: '可选商品', value: products.value.length },
    { label: '购物车', value: cartCount.value },
    { label: '历史订单', value: orders.value.length },
  ]
})

watch(
  role,
  async () => {
    await refresh()
  },
  { immediate: true },
)

async function refresh() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const currentSession = getSession(role.value)
    if (!currentSession || currentSession.role !== role.value) {
      await router.replace({ path: roleLogin(role.value), query: { redirect: route.fullPath } })
      return
    }
    session.value = currentSession
    if (role.value === 'CUSTOMER') {
      await loadCustomer()
    } else if (role.value === 'MERCHANT') {
      await loadMerchant()
    } else if (role.value === 'RIDER') {
      await loadRider()
    } else {
      await loadAdmin()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
    if (error.value.includes('请先登录')) {
      await router.replace({ path: roleLogin(role.value), query: { redirect: route.fullPath } })
    }
  } finally {
    busy.value = false
  }
}

async function handleLogout() {
  await logout(role.value)
  await router.replace(roleLogin(role.value))
}

async function loadCustomer() {
  home.value = await api<AnyRecord>(null, '/market/home')
  await loadStoresPage(0)
  addresses.value = await api<AnyRecord[]>('CUSTOMER', '/user/addresses')
  selectedAddressId.value = addresses.value.find((address) => address.defaultAddress)?.id ?? addresses.value[0]?.id
  cart.value = await api<AnyRecord[]>('CUSTOMER', '/cart/items')
  orders.value = await api<AnyRecord[]>('CUSTOMER', '/user/orders')
  customerOrderPage.value = { ...customerOrderPage.value, page: 0, total: orders.value.length }
}

function openCreateAddress() {
  addressDialog.value = {
    open: true,
    mode: 'create',
    address: null,
    receiver: session.value?.displayName || '',
    phone: '13800001111',
    detail: '',
    distanceKm: 2,
    inRange: true,
    defaultAddress: addresses.value.length === 0,
  }
}

function openEditAddress(address: AnyRecord) {
  addressDialog.value = {
    open: true,
    mode: 'edit',
    address,
    receiver: address.receiver,
    phone: address.phoneMasked,
    detail: address.detailMasked,
    distanceKm: Number(address.distanceKm || 2),
    inRange: Boolean(address.inRange),
    defaultAddress: Boolean(address.defaultAddress),
  }
}

async function submitAddress() {
  const body = {
    receiver: addressDialog.value.receiver,
    phone: addressDialog.value.phone,
    detail: addressDialog.value.detail,
    distanceKm: addressDialog.value.distanceKm,
    inRange: addressDialog.value.inRange,
    defaultAddress: addressDialog.value.defaultAddress,
  }
  if (addressDialog.value.mode === 'edit' && addressDialog.value.address) {
    await api<AnyRecord>('CUSTOMER', `/user/addresses/${addressDialog.value.address.id}`, {
      method: 'PATCH',
      body,
    })
    notice.value = '收货地址已更新'
  } else {
    await api<AnyRecord>('CUSTOMER', '/user/addresses', {
      method: 'POST',
      body,
    })
    notice.value = '收货地址已新增'
  }
  addressDialog.value.open = false
  await loadCustomer()
}

async function setDefaultAddress(address: AnyRecord) {
  await api<AnyRecord>('CUSTOMER', `/user/addresses/${address.id}/default`, { method: 'POST' })
  notice.value = `${address.receiver} 已设为默认地址`
  await loadCustomer()
}

async function deleteAddress(address: AnyRecord) {
  await api<AnyRecord[]>('CUSTOMER', `/user/addresses/${address.id}`, { method: 'DELETE' })
  notice.value = '收货地址已删除'
  await loadCustomer()
}

async function loadStoresPage(pageNo = 0) {
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
  params.set('size', String(storePage.value.size))
  const page = await api<AnyRecord>(null, `/stores/page?${params}`)
  storePage.value = { page: page.page, size: page.size, total: page.total, hasMore: page.hasMore }
  stores.value = page.records
  const current = stores.value.find((store) => store.id === selectedStoreId.value) || stores.value[0]
  if (current) {
    await selectStore(current)
  } else {
    selectedStore.value = null
    products.value = []
  }
}

async function selectStore(store: AnyRecord) {
  selectedStoreId.value = Number(store.id)
  selectedStore.value = await api<AnyRecord>(null, `/stores/${store.id}`)
  products.value = await api<AnyRecord[]>(null, `/stores/${store.id}/products`)
  activeProductCategory.value = '全部'
}

async function applyCategory(category: string) {
  activeCategory.value = category
  await loadStoresPage(0)
}

async function applySort(sort: string) {
  activeSort.value = sort
  await loadStoresPage(0)
}

async function searchStores() {
  await loadStoresPage(0)
}

async function clearCart() {
  const productIds = cart.value.map((item) => item.productId)
  for (const productId of productIds) {
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

async function removeCartItem(productId: number) {
  cart.value = await api<AnyRecord[]>('CUSTOMER', `/cart/items/${productId}`, { method: 'DELETE' })
}

async function createOrder() {
  const token = await api<{ token: string }>('CUSTOMER', '/orders/idempotency-token')
  const order = await api<AnyRecord>('CUSTOMER', '/orders', {
    method: 'POST',
    body: {
      storeId: cart.value[0]?.storeId ?? selectedStoreId.value,
      addressId: selectedAddressId.value,
      idempotencyToken: token.token,
      couponCode: couponCode.value,
      items: cart.value.map((item) => ({ productId: item.productId, quantity: item.quantity })),
    },
  })
  notice.value = `订单 ${order.orderNo} 已提交，等待支付`
  await loadCustomer()
}

async function payOrder(order: AnyRecord) {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${order.id}/pay`, {
    method: 'POST',
    body: { callbackFlowNo: `UI-${order.id}-${Date.now()}` },
  })
  notice.value = `${order.orderNo} 支付成功`
  await loadCustomer()
}

async function confirmOrder(order: AnyRecord) {
  await api<AnyRecord>('CUSTOMER', `/user/orders/${order.id}/confirm`, { method: 'POST' })
  notice.value = `${order.orderNo} 已确认收货`
  await loadCustomer()
}

async function refundOrder(order: AnyRecord) {
  refundDialog.value = {
    ...refundDialog.value,
    open: true,
    order,
    reason: '配送时间过长',
    detail: `订单 ${order.orderNo} 预计履约时间超出预期，申请退款。`,
  }
}

async function submitRefund() {
  const order = refundDialog.value.order
  if (!order) {
    return
  }
  await api<AnyRecord>('CUSTOMER', `/user/orders/${order.id}/refund`, {
    method: 'POST',
    body: { reason: `${refundDialog.value.reason}：${refundDialog.value.detail}` },
  })
  notice.value = `${order.orderNo} 已提交退款处理`
  refundDialog.value.open = false
  await loadCustomer()
}

async function reviewOrder(order: AnyRecord) {
  reviewDialog.value = {
    ...reviewDialog.value,
    open: true,
    order,
    score: 5,
    content: '味道不错，配送及时，包装完整。',
  }
}

async function submitReview() {
  const order = reviewDialog.value.order
  if (!order) {
    return
  }
  await api<AnyRecord>('CUSTOMER', `/user/orders/${order.id}/review`, {
    method: 'POST',
    body: { score: reviewDialog.value.score, content: reviewDialog.value.content },
  })
  notice.value = `${order.orderNo} 已评价`
  reviewDialog.value.open = false
  await loadCustomer()
}

async function loadMerchant() {
  merchantStore.value = await api<AnyRecord>('MERCHANT', '/merchant/store')
  syncStoreDraft()
  await loadMerchantProducts(0)
  pendingOrders.value = await api<AnyRecord[]>('MERCHANT', '/merchant/orders/pending')
  pendingOrderPage.value = { ...pendingOrderPage.value, page: 0, total: pendingOrders.value.length }
  await loadMerchantOrders(0)
}

function syncStoreDraft() {
  const store = merchantStore.value
  if (!store) {
    return
  }
  storeDraft.value = {
    name: store.name || '',
    notice: store.notice || '',
    open: Boolean(store.open),
    minDeliveryAmount: String(store.minDeliveryAmount ?? '20.00'),
    deliveryFee: String(store.deliveryFee ?? '4.00'),
    avgDeliveryMinutes: Number(store.avgDeliveryMinutes || 30),
    deliveryRangeKm: Number(store.deliveryRangeKm || 6),
    businessHours: store.businessHours || '09:00-22:00',
    deliveryGuarantee: store.deliveryGuarantee || '准时宝',
    statusMessage: store.statusMessage || '',
    tagsText: (store.tags || []).join('、'),
    promotionsText: (store.promotions || []).join('、'),
    couponHintsText: (store.couponHints || []).join('、'),
  }
}

function splitTextList(value: string) {
  return value
    .split(/[、,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function saveStoreSettings() {
  merchantStore.value = await api<AnyRecord>('MERCHANT', '/merchant/store', {
    method: 'PATCH',
    body: {
      name: storeDraft.value.name,
      notice: storeDraft.value.notice,
      open: storeDraft.value.open,
      minDeliveryAmount: storeDraft.value.minDeliveryAmount,
      deliveryFee: storeDraft.value.deliveryFee,
      avgDeliveryMinutes: storeDraft.value.avgDeliveryMinutes,
      deliveryRangeKm: storeDraft.value.deliveryRangeKm,
      businessHours: storeDraft.value.businessHours,
      deliveryGuarantee: storeDraft.value.deliveryGuarantee,
      statusMessage: storeDraft.value.statusMessage,
      tags: splitTextList(storeDraft.value.tagsText),
      promotions: splitTextList(storeDraft.value.promotionsText),
      couponHints: splitTextList(storeDraft.value.couponHintsText),
    },
  })
  syncStoreDraft()
  notice.value = '门店经营配置已保存'
}

async function toggleStoreOpen(open: boolean) {
  merchantStore.value = await api<AnyRecord>('MERCHANT', open ? '/merchant/store/open' : '/merchant/store/close', {
    method: 'POST',
  })
  syncStoreDraft()
  notice.value = open ? '门店已开店营业' : '门店已打烊'
  await loadMerchant()
}

async function loadMerchantProducts(pageNo = 0) {
  const page = await api<AnyRecord>(
    'MERCHANT',
    `/merchant/products/page?page=${Math.max(0, pageNo)}&size=${merchantProductPage.value.size}`,
    {},
  )
  merchantProductPage.value = { page: page.page, size: page.size, total: page.total, hasMore: page.hasMore }
  merchantProducts.value = page.records
}

async function loadMerchantOrders(pageNo = 0) {
  const page = await api<AnyRecord>(
    'MERCHANT',
    `/merchant/orders/page?page=${Math.max(0, pageNo)}&size=${merchantOrderPage.value.size}`,
    {},
  )
  merchantOrderPage.value = { page: page.page, size: page.size, total: page.total, hasMore: page.hasMore }
  merchantOrders.value = page.records
}

async function saveProduct() {
  await api<AnyRecord>('MERCHANT', '/merchant/products', {
    method: 'POST',
    body: productDraft.value,
  })
  notice.value = '商品已保存'
  await loadMerchantProducts(0)
}

async function updateProduct(product: AnyRecord, patch: Partial<AnyRecord>) {
  await api<AnyRecord>('MERCHANT', `/merchant/products/${product.id}`, {
    method: 'PATCH',
    body: {
      id: product.id,
      name: product.name,
      description: product.description,
      price: String(product.price),
      stock: product.stock,
      onSale: product.onSale,
      category: product.category,
      imageTone: product.imageTone,
      discountLabel: product.discountLabel,
      originalPrice: String(product.originalPrice ?? product.price),
      ...patch,
    },
  })
  await loadMerchantProducts(merchantProductPage.value.page)
}

async function acceptOrder(order: AnyRecord) {
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${order.id}/accept`, { method: 'POST' })
  notice.value = `${order.orderNo} 已接单`
  await loadMerchant()
}

async function rejectOrder(order: AnyRecord) {
  rejectOrderDialog.value = {
    ...rejectOrderDialog.value,
    open: true,
    order,
    reason: '高峰期爆单，预计无法按时出餐',
    detail: `订单 ${order.orderNo} 当前无法按承诺时效履约，建议自动退款。`,
  }
}

async function submitRejectOrder() {
  const order = rejectOrderDialog.value.order
  if (!order) {
    return
  }
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${order.id}/reject`, {
    method: 'POST',
    body: { reason: `${rejectOrderDialog.value.reason}：${rejectOrderDialog.value.detail}` },
  })
  notice.value = `${order.orderNo} 已拒单并退款`
  rejectOrderDialog.value.open = false
  await loadMerchant()
}

async function readyOrder(order: AnyRecord) {
  await api<AnyRecord>('MERCHANT', `/merchant/orders/${order.id}/ready`, { method: 'POST' })
  notice.value = `${order.orderNo} 已出餐`
  await loadMerchant()
}

async function handleBannerClick(banner: AnyRecord) {
  pendingBanner.value = banner
  await claimBanner(banner, '')
}

async function claimBanner(banner: AnyRecord, captchaAnswer: string) {
  try {
    error.value = ''
    const claim = await api<AnyRecord>('CUSTOMER', `/marketing/coupons/${banner.sceneCode}/claim`, {
      method: 'POST',
      body: { captchaAnswer },
    })
    couponCode.value = claim.firstCouponCode
    captchaDialog.value.open = false
    captchaDialog.value.answer = ''
    if (banner.sceneCode === 'MEMBER') {
      notice.value = `${claim.message}，批次 ${claim.batchCode}，发放 ${claim.issuedCount} 张，剩余券包 ${claim.remainingStock}`
      return
    }
    if (banner.sceneCode === 'FAST') {
      activeSort.value = 'distance'
      notice.value = `${claim.message}，已切换到距离最近`
      await loadStoresPage(0)
      return
    }
    if (banner.sceneCode === 'B2B') {
      activeSort.value = 'sales'
      notice.value = `${claim.message}，已进入企业午餐视角`
      await loadStoresPage(0)
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    if (message.includes('验证码') || message.includes('3+5')) {
      captchaDialog.value = {
        open: true,
        answer: '',
        message,
        sceneCode: banner.sceneCode,
      }
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

async function loadRider() {
  availableRiderTasks.value = await api<AnyRecord[]>('RIDER', '/rider/tasks/available')
  riderTasks.value = await api<AnyRecord[]>('RIDER', '/rider/tasks')
  availableRiderPage.value = { ...availableRiderPage.value, total: availableRiderTasks.value.length }
  riderTaskPage.value = { ...riderTaskPage.value, total: riderTasks.value.length }
}

async function acceptTask(task: AnyRecord) {
  await api<AnyRecord>('RIDER', `/rider/tasks/${task.id}/accept`, { method: 'POST' })
  notice.value = `任务 ${task.id} 已接单`
  await loadRider()
}

async function arriveStore(task: AnyRecord) {
  await api<AnyRecord>('RIDER', `/rider/tasks/${task.id}/arrive-store`, { method: 'POST' })
  notice.value = `任务 ${task.id} 已到店`
  await loadRider()
}

async function pickupTask(task: AnyRecord) {
  await api<AnyRecord>('RIDER', `/rider/tasks/${task.id}/pickup`, { method: 'POST' })
  notice.value = `任务 ${task.id} 已取餐`
  await loadRider()
}

async function deliverTask(task: AnyRecord) {
  await api<AnyRecord>('RIDER', `/rider/tasks/${task.id}/deliver`, { method: 'POST' })
  notice.value = `任务 ${task.id} 已送达`
  await loadRider()
}

async function reportException(task: AnyRecord) {
  riderExceptionDialog.value = {
    ...riderExceptionDialog.value,
    open: true,
    task,
    type: '联系不上用户',
    detail: '电话多次无人接听，已在收货地址附近等待超过 10 分钟。',
    evidenceNo: `EV-${task.id}-${Date.now()}`,
  }
}

async function submitRiderException() {
  const task = riderExceptionDialog.value.task
  if (!task) {
    return
  }
  await api<AnyRecord>('RIDER', `/rider/tasks/${task.id}/exception`, {
    method: 'POST',
    body: {
      type: riderExceptionDialog.value.type,
      detail: riderExceptionDialog.value.detail,
      evidenceNo: riderExceptionDialog.value.evidenceNo,
      reason: `${riderExceptionDialog.value.type}：${riderExceptionDialog.value.detail}（凭证 ${riderExceptionDialog.value.evidenceNo}）`,
    },
  })
  notice.value = `任务 ${task.id} 已上报异常`
  riderExceptionDialog.value.open = false
  await loadRider()
}

async function loadAdmin() {
  adminOrders.value = await api<AnyRecord[]>('ADMIN', '/admin/orders')
  tickets.value = await api<AnyRecord[]>('ADMIN', '/admin/tickets')
  riskRecords.value = await api<AnyRecord[]>('ADMIN', '/admin/risk-records')
  auditLogs.value = await api<AnyRecord[]>('ADMIN', '/admin/audit-logs')
  snapshot.value = await api<AnyRecord>('ADMIN', '/demo/snapshot')
  adminOrderPage.value = { ...adminOrderPage.value, total: adminOrders.value.length }
  ticketPage.value = { ...ticketPage.value, total: tickets.value.length }
  riskPage.value = { ...riskPage.value, total: riskRecords.value.length }
  auditPage.value = { ...auditPage.value, total: auditLogs.value.length }
}

async function approveTicket(ticket: AnyRecord) {
  ticketDialog.value = {
    open: true,
    ticket,
    action: 'approve',
    result: `审核通过，订单 ${ticket.orderId} 退款成功，售后原因与订单状态一致。`,
  }
}

async function rejectTicket(ticket: AnyRecord) {
  ticketDialog.value = {
    open: true,
    ticket,
    action: 'reject',
    result: `材料不足，订单 ${ticket.orderId} 需补充照片、聊天记录或支付凭证后再处理。`,
  }
}

async function submitTicketDecision() {
  const ticket = ticketDialog.value.ticket
  if (!ticket) {
    return
  }
  const path =
    ticketDialog.value.action === 'approve'
      ? `/admin/tickets/${ticket.id}/approve`
      : `/admin/tickets/${ticket.id}/reject`
  await api<AnyRecord>('ADMIN', path, {
    method: 'POST',
    body: { result: ticketDialog.value.result },
  })
  notice.value = `工单 ${ticket.id} 已${ticketDialog.value.action === 'approve' ? '通过' : '驳回'}`
  ticketDialog.value.open = false
  await loadAdmin()
}

function pageInfo(total: number, state: PageState): PageState {
  const totalPages = Math.max(1, Math.ceil(total / state.size))
  const page = Math.min(Math.max(0, state.page), totalPages - 1)
  return { ...state, page, total, hasMore: page < totalPages - 1 }
}

function pageSlice<T>(items: T[], state: PageState) {
  const info = pageInfo(items.length, state)
  return items.slice(info.page * info.size, info.page * info.size + info.size)
}

function totalPages(state: PageState) {
  return Math.max(1, Math.ceil(Number(state.total || 0) / Number(state.size || 1)))
}

function visiblePageNumbers(state: PageState) {
  const total = totalPages(state)
  const current = Math.min(Math.max(0, state.page), total - 1)
  const windowSize = Math.min(10, total)
  let start = Math.max(0, current - Math.floor(windowSize / 2))
  start = Math.min(start, Math.max(0, total - windowSize))
  return Array.from({ length: windowSize }, (_, index) => start + index)
}

function setLocalPage(state: PageState, total: number, pageNo: number) {
  const info = pageInfo(total, state)
  state.page = Math.min(Math.max(0, pageNo), totalPages(info) - 1)
  state.total = total
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function dateTime(value: string | undefined) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function statusText(status: string) {
  const names: Record<string, string> = {
    WAIT_PAY: '待支付',
    PAID_WAIT_ACCEPT: '待接单',
    PREPARING: '备餐中',
    WAIT_PICKUP: '待取餐',
    DELIVERING: '配送中',
    DELIVERED: '已送达',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDING: '退款中',
    REFUNDED: '已退款',
    AFTERSALE: '售后中',
  }
  return names[status] || status
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">{{ roleTitle }}</p>
        <h1>{{ roleSummary }}</h1>
      </div>
      <div class="account-chip">
        <span>{{ session?.displayName }}</span>
        <strong>{{ role }}</strong>
        <button type="button" @click="handleLogout">退出</button>
      </div>
    </header>

    <div class="kpi-row">
      <article v-for="item in kpis" :key="item.label" class="kpi">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <template v-if="role === 'CUSTOMER'">
      <section class="market-hero">
        <div class="location-card">
          <span>当前配送地址</span>
          <strong>{{ addresses.find((address) => address.id === selectedAddressId)?.detailMasked || '加载地址中' }}</strong>
          <small>共 {{ storePage.total || stores.length }} 家可配送门店，当前第 {{ storePage.page + 1 }} 页</small>
          <button type="button" @click="openCreateAddress">新增地址</button>
        </div>

        <div class="search-card">
          <label class="search-box">
            <span>搜索</span>
            <input v-model="keyword" placeholder="搜索商家、商品、品类" @keyup.enter="searchStores" />
            <button type="button" @click="searchStores">查找</button>
          </label>
          <div class="sort-row">
            <button type="button" :class="{ active: activeSort === 'default' }" @click="applySort('default')">
              综合排序
            </button>
            <button type="button" :class="{ active: activeSort === 'sales' }" @click="applySort('sales')">销量优先</button>
            <button type="button" :class="{ active: activeSort === 'rating' }" @click="applySort('rating')">评分最高</button>
            <button type="button" :class="{ active: activeSort === 'distance' }" @click="applySort('distance')">距离最近</button>
          </div>
        </div>
      </section>

      <section class="channel-strip">
        <button
          v-for="category in categories"
          :key="category"
          type="button"
          :class="{ active: activeCategory === category }"
          @click="applyCategory(category)"
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

      <div class="market-layout">
        <aside class="store-rank-panel">
          <div class="panel-heading">
            <h2>附近好店</h2>
            <span>第 {{ storePage.page + 1 }} / {{ totalPages(storePage) }} 页，共 {{ storePage.total }} 家</span>
          </div>
          <div class="store-list">
            <button
              v-for="store in stores"
              :key="store.id"
              type="button"
              class="store-row"
              :class="{ active: selectedStoreId === store.id }"
              @click="selectStore(store)"
            >
              <span class="store-logo">{{ store.logoText }}</span>
              <span class="store-copy">
                <strong>{{ store.name }}</strong>
                <small>{{ store.rating }} 分 / 月售 {{ store.monthlySales }} / {{ store.avgDeliveryMinutes }} 分钟</small>
                <em>{{ store.promotions?.[0] }}</em>
              </span>
            </button>
            <div v-if="stores.length === 0" class="empty-state">暂无匹配门店</div>
          </div>
          <nav v-if="totalPages(storePage) > 1" class="pager" aria-label="附近好店分页">
            <button type="button" :disabled="storePage.page === 0" @click="loadStoresPage(storePage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(storePage)"
              :key="pageNo"
              type="button"
              :class="{ active: storePage.page === pageNo }"
              @click="loadStoresPage(pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button
              type="button"
              :disabled="storePage.page >= totalPages(storePage) - 1"
              @click="loadStoresPage(storePage.page + 1)"
            >
              ›
            </button>
          </nav>
        </aside>

        <main class="store-detail-panel">
          <section v-if="selectedStore" class="store-hero-card">
            <div class="store-logo xl">{{ selectedStore.logoText }}</div>
            <div>
              <div class="store-title-line">
                <h2>{{ selectedStore.name }}</h2>
                <span>{{ selectedStore.category }}</span>
              </div>
              <p>{{ selectedStore.notice }}</p>
              <div class="store-metrics">
                <span>{{ selectedStore.rating }} 分</span>
                <span>月售 {{ selectedStore.monthlySales }}</span>
                <span>{{ selectedStore.distanceKm }}km</span>
                <span>{{ selectedStore.avgDeliveryMinutes }} 分钟</span>
                <span>起送 {{ money(selectedStore.minDeliveryAmount) }}</span>
                <span>配送 {{ money(selectedStore.deliveryFee) }}</span>
              </div>
              <div class="coupon-line">
                <span v-for="promo in activeStorePromotions" :key="promo">{{ promo }}</span>
              </div>
            </div>
          </section>

          <section class="menu-board">
            <nav class="menu-tabs">
              <button
                v-for="category in productCategories"
                :key="category"
                type="button"
                :class="{ active: activeProductCategory === category }"
                @click="activeProductCategory = category"
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
                    <del v-if="Number(product.originalPrice) > Number(product.price)">
                      {{ money(product.originalPrice) }}
                    </del>
                    <button type="button" :disabled="!product.onSale || product.stock < 1" @click="addToCart(product)">
                      加购
                    </button>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </main>

        <aside class="checkout-panel">
          <div class="panel-heading">
            <h2>购物车</h2>
            <span>{{ cartCount }} 件</span>
          </div>
          <div class="cart-list">
            <article v-for="item in cart" :key="item.productId" class="compact-row">
              <div>
                <strong>{{ item.productName }}</strong>
                <span>{{ money(item.price) }} × {{ item.quantity }}</span>
              </div>
              <div class="row-actions">
                <span>{{ money(item.subtotal) }}</span>
                <button type="button" @click="removeCartItem(item.productId)">移除</button>
              </div>
            </article>
          </div>

          <label class="field">
            <span>收货地址</span>
            <select v-model.number="selectedAddressId">
              <option v-for="address in addresses" :key="address.id" :value="address.id" :disabled="!address.inRange">
                {{ address.receiver }} / {{ address.detailMasked }} / {{ address.inRange ? '可配送' : '超范围' }}
              </option>
            </select>
          </label>

          <div class="address-actions">
            <article v-for="address in addresses" :key="address.id" class="mini-row">
              <div>
                <strong>{{ address.receiver }}{{ address.defaultAddress ? ' · 默认' : '' }}</strong>
                <span>{{ address.detailMasked }}</span>
              </div>
              <div class="row-actions">
                <button type="button" @click="openEditAddress(address)">编辑</button>
                <button type="button" :disabled="address.defaultAddress" @click="setDefaultAddress(address)">默认</button>
                <button type="button" :disabled="addresses.length <= 1" @click="deleteAddress(address)">删除</button>
              </div>
            </article>
          </div>

          <label class="field">
            <span>优惠券</span>
            <input v-model="couponCode" />
          </label>

          <div class="checkout-total">
            <span>商品小计</span>
            <strong>{{ money(cartTotal) }}</strong>
          </div>
          <button class="primary" type="button" :disabled="cart.length === 0" @click="createOrder">去结算</button>
        </aside>
      </div>

      <section class="panel">
        <div class="panel-heading">
          <h2>订单追踪</h2>
          <span>{{ orders.length }} 单</span>
        </div>
        <div class="order-grid">
          <article v-for="order in pagedCustomerOrders" :key="order.id" class="order-card">
            <div class="order-main">
              <div>
                <span class="status-pill">{{ statusText(order.status) }}</span>
                <h3>{{ order.orderNo }} · {{ order.storeName }}</h3>
                <p>{{ order.items.map((item: AnyRecord) => `${item.productName}×${item.quantity}`).join('，') }}</p>
              </div>
              <strong>{{ money(order.payAmount) }}</strong>
            </div>
            <div class="tracking-line">
              <span v-for="step in order.fulfillmentSteps" :key="step.id">{{ step.title }}</span>
            </div>
            <div class="button-row">
              <button v-if="order.status === 'WAIT_PAY'" type="button" @click="payOrder(order)">模拟支付</button>
              <button
                v-if="!['CANCELLED', 'REFUNDED', 'COMPLETED'].includes(order.status)"
                type="button"
                @click="refundOrder(order)"
              >
                申请退款
              </button>
              <button v-if="order.status === 'DELIVERED'" type="button" @click="confirmOrder(order)">确认收货</button>
              <button v-if="order.status === 'COMPLETED' && !order.review" type="button" @click="reviewOrder(order)">评价</button>
            </div>
          </article>
          <div v-if="orders.length === 0" class="empty-state">暂无订单</div>
        </div>
        <nav v-if="totalPages(pageInfo(orders.length, customerOrderPage)) > 1" class="pager" aria-label="用户订单分页">
          <button type="button" :disabled="customerOrderPage.page === 0" @click="setLocalPage(customerOrderPage, orders.length, customerOrderPage.page - 1)">‹</button>
          <button
            v-for="pageNo in visiblePageNumbers(pageInfo(orders.length, customerOrderPage))"
            :key="pageNo"
            type="button"
            :class="{ active: customerOrderPage.page === pageNo }"
            @click="setLocalPage(customerOrderPage, orders.length, pageNo)"
          >
            {{ pageNo + 1 }}
          </button>
          <button
            type="button"
            :disabled="customerOrderPage.page >= totalPages(pageInfo(orders.length, customerOrderPage)) - 1"
            @click="setLocalPage(customerOrderPage, orders.length, customerOrderPage.page + 1)"
          >
            ›
          </button>
        </nav>
      </section>
    </template>

    <template v-else-if="role === 'MERCHANT'">
      <div class="ops-layout">
        <section class="ops-hero">
          <div>
            <p class="eyebrow">经营总览</p>
            <h2>{{ session?.displayName }}实时经营台</h2>
            <p>当前账号只展示所属门店、商品和订单，账号创建和重置由后台管理员完成。</p>
          </div>
          <div class="ops-metrics">
            <span>{{ merchantStore?.open ? '营业中' : '已打烊' }}</span>
            <span>营业 {{ merchantStore?.businessHours || '-' }}</span>
            <span>库存预警 {{ merchantProducts.filter((product) => product.stock < 20).length }}</span>
          </div>
        </section>

        <section class="panel wide-panel">
          <div class="panel-heading">
            <h2>门店经营配置</h2>
            <div class="row-actions">
              <button v-if="merchantStore?.open" type="button" @click="toggleStoreOpen(false)">打烊</button>
              <button v-else type="button" @click="toggleStoreOpen(true)">开店</button>
            </div>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>门店名</span>
              <input v-model="storeDraft.name" />
            </label>
            <label class="field">
              <span>起送价</span>
              <input v-model="storeDraft.minDeliveryAmount" />
            </label>
            <label class="field">
              <span>配送费</span>
              <input v-model="storeDraft.deliveryFee" />
            </label>
            <label class="field">
              <span>营业时间</span>
              <input v-model="storeDraft.businessHours" />
            </label>
            <label class="field">
              <span>平均送达分钟</span>
              <input v-model.number="storeDraft.avgDeliveryMinutes" type="number" min="10" max="120" />
            </label>
            <label class="field">
              <span>配送范围 km</span>
              <input v-model.number="storeDraft.deliveryRangeKm" type="number" min="0.5" step="0.5" />
            </label>
            <label class="field field-wide">
              <span>公告</span>
              <input v-model="storeDraft.notice" />
            </label>
            <label class="field field-wide">
              <span>履约说明</span>
              <input v-model="storeDraft.statusMessage" />
            </label>
            <label class="field">
              <span>标签</span>
              <input v-model="storeDraft.tagsText" />
            </label>
            <label class="field">
              <span>活动</span>
              <input v-model="storeDraft.promotionsText" />
            </label>
            <label class="field">
              <span>券提示</span>
              <input v-model="storeDraft.couponHintsText" />
            </label>
            <button class="primary" type="button" @click="saveStoreSettings">保存门店配置</button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>待处理订单</h2>
            <span>{{ pendingOrders.length }} 单</span>
          </div>
          <div class="order-grid">
            <article v-for="order in pagedPendingOrders" :key="order.id" class="order-card">
              <div class="order-main">
                <div>
                  <span class="status-pill">{{ statusText(order.status) }}</span>
                  <h3>{{ order.orderNo }}</h3>
                  <p>{{ order.customerName }} / {{ money(order.payAmount) }}</p>
                </div>
              </div>
              <div class="button-row">
                <button type="button" @click="acceptOrder(order)">接单</button>
                <button type="button" @click="rejectOrder(order)">拒单退款</button>
              </div>
            </article>
            <div v-if="pendingOrders.length === 0" class="empty-state">暂无待处理订单</div>
          </div>
          <nav v-if="totalPages(pendingOrderPager) > 1" class="pager" aria-label="待处理订单分页">
            <button type="button" :disabled="pendingOrderPage.page === 0" @click="setLocalPage(pendingOrderPage, pendingOrders.length, pendingOrderPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(pendingOrderPager)"
              :key="pageNo"
              type="button"
              :class="{ active: pendingOrderPage.page === pageNo }"
              @click="setLocalPage(pendingOrderPage, pendingOrders.length, pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button type="button" :disabled="pendingOrderPage.page >= totalPages(pendingOrderPager) - 1" @click="setLocalPage(pendingOrderPage, pendingOrders.length, pendingOrderPage.page + 1)">›</button>
          </nav>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>商品供给</h2>
            <span>{{ merchantProducts.length }} / {{ merchantProductPage.total }} 个商品</span>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>商品名</span>
              <input v-model="productDraft.name" />
            </label>
            <label class="field">
              <span>价格</span>
              <input v-model="productDraft.price" />
            </label>
            <label class="field">
              <span>库存</span>
              <input v-model.number="productDraft.stock" type="number" min="0" />
            </label>
            <label class="field">
              <span>分类</span>
              <input v-model="productDraft.category" />
            </label>
            <label class="field">
              <span>原价</span>
              <input v-model="productDraft.originalPrice" />
            </label>
            <label class="field">
              <span>展示色调</span>
              <select v-model="productDraft.imageTone">
                <option value="rice">米饭</option>
                <option value="spicy">辣味</option>
                <option value="fresh">轻食</option>
                <option value="drink">饮品</option>
                <option value="market">商超</option>
                <option value="default">默认</option>
              </select>
            </label>
            <label class="field">
              <span>营销标签</span>
              <input v-model="productDraft.discountLabel" />
            </label>
            <label class="field field-wide">
              <span>描述</span>
              <input v-model="productDraft.description" />
            </label>
            <button class="primary" type="button" @click="saveProduct">新增商品</button>
          </div>
          <div class="inventory-table">
            <article v-for="product in merchantProducts" :key="product.id" class="compact-row">
              <div>
                <strong>{{ product.name }}</strong>
                <span>{{ product.category || '商品' }} / {{ money(product.price) }} / 库存 {{ product.stock }}</span>
              </div>
              <div class="row-actions">
                <button type="button" @click="updateProduct(product, { stock: product.stock + 10 })">补货</button>
                <button type="button" @click="updateProduct(product, { onSale: !product.onSale })">
                  {{ product.onSale ? '下架' : '上架' }}
                </button>
              </div>
            </article>
            <div v-if="merchantProducts.length === 0" class="empty-state">当前商家暂无商品</div>
          </div>
          <nav v-if="totalPages(merchantProductPage) > 1" class="pager" aria-label="商品分页">
            <button type="button" :disabled="merchantProductPage.page === 0" @click="loadMerchantProducts(merchantProductPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(merchantProductPage)"
              :key="pageNo"
              type="button"
              :class="{ active: merchantProductPage.page === pageNo }"
              @click="loadMerchantProducts(pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button
              type="button"
              :disabled="merchantProductPage.page >= totalPages(merchantProductPage) - 1"
              @click="loadMerchantProducts(merchantProductPage.page + 1)"
            >
              ›
            </button>
          </nav>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>全部订单</h2>
            <span>{{ merchantOrders.length }} / {{ merchantOrderPage.total }} 单</span>
          </div>
          <div class="order-grid">
            <article v-for="order in merchantOrders" :key="order.id" class="order-card">
              <div class="order-main">
                <div>
                  <span class="status-pill">{{ statusText(order.status) }}</span>
                  <h3>{{ order.orderNo }} · {{ order.customerName }}</h3>
                  <p>{{ order.items.map((item: AnyRecord) => `${item.productName}×${item.quantity}`).join('，') }}</p>
                </div>
                <strong>{{ money(order.payAmount) }}</strong>
              </div>
              <div class="button-row">
                <button v-if="order.status === 'PAID_WAIT_ACCEPT'" type="button" @click="acceptOrder(order)">接单</button>
                <button v-if="order.status === 'PREPARING'" type="button" @click="readyOrder(order)">出餐</button>
              </div>
            </article>
            <div v-if="merchantOrders.length === 0" class="empty-state">当前商家暂无订单</div>
          </div>
          <nav v-if="totalPages(merchantOrderPage) > 1" class="pager" aria-label="商家订单分页">
            <button type="button" :disabled="merchantOrderPage.page === 0" @click="loadMerchantOrders(merchantOrderPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(merchantOrderPage)"
              :key="pageNo"
              type="button"
              :class="{ active: merchantOrderPage.page === pageNo }"
              @click="loadMerchantOrders(pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button
              type="button"
              :disabled="merchantOrderPage.page >= totalPages(merchantOrderPage) - 1"
              @click="loadMerchantOrders(merchantOrderPage.page + 1)"
            >
              ›
            </button>
          </nav>
        </section>
      </div>
    </template>

    <template v-else-if="role === 'RIDER'">
      <section class="ops-hero rider-hero">
        <div>
          <p class="eyebrow">骑手工作台</p>
          <h2>按任务状态推进配送履约</h2>
          <p>从可接任务池抢单，到店、取餐、送达和异常上报都会写配送状态日志。</p>
        </div>
        <div class="ops-metrics">
          <span>可接 {{ availableRiderTasks.length }}</span>
          <span>我的任务 {{ riderTasks.length }}</span>
          <span>异常 {{ riderTasks.filter((task) => task.exceptionReason).length }}</span>
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h2>可接任务池</h2>
          <button type="button" @click="refresh">刷新</button>
        </div>
        <div class="task-grid">
          <article v-for="task in pagedAvailableRiderTasks" :key="task.id" class="order-card rider-task">
            <div class="order-main">
              <div>
                <span class="status-pill">{{ task.status }}</span>
                <h3>任务 {{ task.id }} / 订单 {{ task.orderId }}</h3>
                <p>{{ task.currentStep }}</p>
              </div>
            </div>
            <div class="route-mini">
              <span>商家</span>
              <i></i>
              <span>待接单</span>
              <i></i>
              <span>用户</span>
            </div>
            <div class="button-row">
              <button type="button" @click="acceptTask(task)">接单</button>
            </div>
          </article>
          <div v-if="availableRiderTasks.length === 0" class="empty-state">暂无可接任务</div>
        </div>
        <nav v-if="totalPages(availableRiderPager) > 1" class="pager" aria-label="可接任务分页">
          <button type="button" :disabled="availableRiderPage.page === 0" @click="setLocalPage(availableRiderPage, availableRiderTasks.length, availableRiderPage.page - 1)">‹</button>
          <button
            v-for="pageNo in visiblePageNumbers(availableRiderPager)"
            :key="pageNo"
            type="button"
            :class="{ active: availableRiderPage.page === pageNo }"
            @click="setLocalPage(availableRiderPage, availableRiderTasks.length, pageNo)"
          >
            {{ pageNo + 1 }}
          </button>
          <button type="button" :disabled="availableRiderPage.page >= totalPages(availableRiderPager) - 1" @click="setLocalPage(availableRiderPage, availableRiderTasks.length, availableRiderPage.page + 1)">›</button>
        </nav>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h2>我的配送任务</h2>
          <button type="button" @click="refresh">刷新</button>
        </div>
        <div class="task-grid">
          <article v-for="task in pagedRiderTasks" :key="task.id" class="order-card rider-task">
            <div class="order-main">
              <div>
                <span class="status-pill">{{ task.status }}</span>
                <h3>任务 {{ task.id }} / 订单 {{ task.orderId }}</h3>
                <p>{{ task.currentStep }}</p>
                <p v-if="task.exceptionReason">异常：{{ task.exceptionReason }}</p>
              </div>
            </div>
            <div v-if="task.statusLogs?.length" class="tracking-line">
              <span v-for="log in task.statusLogs.slice(-4)" :key="log.id">{{ log.afterStatus }}</span>
            </div>
            <div class="route-mini">
              <span>商家</span>
              <i></i>
              <span>骑手</span>
              <i></i>
              <span>用户</span>
            </div>
            <div class="button-row">
              <button v-if="task.status === 'ACCEPTED'" type="button" @click="arriveStore(task)">确认到店</button>
              <button v-if="task.status === 'ARRIVED_STORE' || task.status === 'WAIT_PICKUP'" type="button" @click="pickupTask(task)">确认取餐</button>
              <button v-if="task.status === 'DELIVERING'" type="button" @click="deliverTask(task)">确认送达</button>
              <button
                v-if="!['DELIVERED', 'EXCEPTION'].includes(task.status)"
                type="button"
                :disabled="Boolean(task.exceptionReason)"
                @click="reportException(task)"
              >
                {{ task.exceptionReason ? '已上报' : '异常上报' }}
              </button>
              <button v-else-if="task.status === 'EXCEPTION'" type="button" disabled>
                已上报
              </button>
            </div>
          </article>
          <div v-if="riderTasks.length === 0" class="empty-state">暂无配送任务</div>
        </div>
        <nav v-if="totalPages(riderTaskPager) > 1" class="pager" aria-label="骑手任务分页">
          <button type="button" :disabled="riderTaskPage.page === 0" @click="setLocalPage(riderTaskPage, riderTasks.length, riderTaskPage.page - 1)">‹</button>
          <button
            v-for="pageNo in visiblePageNumbers(riderTaskPager)"
            :key="pageNo"
            type="button"
            :class="{ active: riderTaskPage.page === pageNo }"
            @click="setLocalPage(riderTaskPage, riderTasks.length, pageNo)"
          >
            {{ pageNo + 1 }}
          </button>
          <button type="button" :disabled="riderTaskPage.page >= totalPages(riderTaskPager) - 1" @click="setLocalPage(riderTaskPage, riderTasks.length, riderTaskPage.page + 1)">›</button>
        </nav>
      </section>
    </template>

    <template v-else>
      <section class="ops-hero admin-hero">
        <div>
          <p class="eyebrow">平台治理</p>
          <h2>订单、售后、风控、审计统一监控</h2>
          <p>用后台视角展示真实平台治理链路，所有敏感操作都有审计记录。</p>
          <div class="button-row">
            <button type="button" @click="router.push('/admin/merchant-accounts')">商家账号映射</button>
            <button type="button" @click="router.push('/admin/onboarding-applications')">入驻审核</button>
            <button type="button" @click="router.push('/admin/coupons')">优惠券治理</button>
          </div>
        </div>
        <div class="ops-metrics">
          <span>Outbox {{ snapshot?.outboxEvents?.length || 0 }}</span>
          <span>库存占用 {{ snapshot?.reservations?.length || 0 }}</span>
          <span>审计 {{ auditLogs.length }}</span>
        </div>
      </section>

      <div class="admin-layout">
        <section class="panel">
          <div class="panel-heading">
            <h2>订单监控</h2>
            <span>{{ adminOrders.length }} 单</span>
          </div>
          <div class="data-list dense">
            <article v-for="order in pagedAdminOrders" :key="order.id" class="compact-row">
              <div>
                <strong>{{ order.orderNo }} · {{ statusText(order.status) }}</strong>
                <span>{{ order.customerName }} / {{ order.storeName }} / {{ money(order.payAmount) }}</span>
              </div>
              <span>{{ dateTime(order.createdAt) }}</span>
            </article>
            <div v-if="adminOrders.length === 0" class="empty-state">暂无订单</div>
          </div>
          <nav v-if="totalPages(adminOrderPager) > 1" class="pager" aria-label="后台订单分页">
            <button type="button" :disabled="adminOrderPage.page === 0" @click="setLocalPage(adminOrderPage, adminOrders.length, adminOrderPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(adminOrderPager)"
              :key="pageNo"
              type="button"
              :class="{ active: adminOrderPage.page === pageNo }"
              @click="setLocalPage(adminOrderPage, adminOrders.length, pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button type="button" :disabled="adminOrderPage.page >= totalPages(adminOrderPager) - 1" @click="setLocalPage(adminOrderPage, adminOrders.length, adminOrderPage.page + 1)">›</button>
          </nav>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>售后工单</h2>
            <span>{{ tickets.length }} 件</span>
          </div>
          <div class="data-list">
            <article v-for="ticket in pagedTickets" :key="ticket.id" class="order-card">
              <div class="order-main">
                <div>
                  <span class="status-pill">{{ ticket.status }}</span>
                  <h3>工单 {{ ticket.id }} / 订单 {{ ticket.orderId }}</h3>
                  <p>{{ ticket.reason }}</p>
                </div>
              </div>
              <div class="button-row">
                <button v-if="ticket.status === 'PENDING'" type="button" @click="approveTicket(ticket)">通过</button>
                <button v-if="ticket.status === 'PENDING'" type="button" @click="rejectTicket(ticket)">驳回</button>
              </div>
            </article>
            <div v-if="tickets.length === 0" class="empty-state">暂无售后工单</div>
          </div>
          <nav v-if="totalPages(ticketPager) > 1" class="pager" aria-label="售后工单分页">
            <button type="button" :disabled="ticketPage.page === 0" @click="setLocalPage(ticketPage, tickets.length, ticketPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(ticketPager)"
              :key="pageNo"
              type="button"
              :class="{ active: ticketPage.page === pageNo }"
              @click="setLocalPage(ticketPage, tickets.length, pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button type="button" :disabled="ticketPage.page >= totalPages(ticketPager) - 1" @click="setLocalPage(ticketPage, tickets.length, ticketPage.page + 1)">›</button>
          </nav>
        </section>
      </div>

      <div class="admin-layout">
        <section class="panel">
          <div class="panel-heading">
            <h2>风控记录</h2>
            <span>{{ riskRecords.length }} 条</span>
          </div>
          <div class="data-list dense">
            <article v-for="record in pagedRiskRecords" :key="record.id" class="compact-row">
              <div>
                <strong>{{ record.type }}</strong>
                <span>{{ record.objectType }} {{ record.objectId }} / {{ record.reason }}</span>
              </div>
              <span>{{ record.status }}</span>
            </article>
            <div v-if="riskRecords.length === 0" class="empty-state">暂无风控记录</div>
          </div>
          <nav v-if="totalPages(riskPager) > 1" class="pager" aria-label="风控记录分页">
            <button type="button" :disabled="riskPage.page === 0" @click="setLocalPage(riskPage, riskRecords.length, riskPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(riskPager)"
              :key="pageNo"
              type="button"
              :class="{ active: riskPage.page === pageNo }"
              @click="setLocalPage(riskPage, riskRecords.length, pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button type="button" :disabled="riskPage.page >= totalPages(riskPager) - 1" @click="setLocalPage(riskPage, riskRecords.length, riskPage.page + 1)">›</button>
          </nav>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <h2>审计日志</h2>
            <span>{{ auditLogs.length }} 条</span>
          </div>
          <div class="data-list dense">
            <article v-for="log in pagedAuditLogs" :key="log.id" class="compact-row">
              <div>
                <strong>{{ log.action }} / {{ log.actorName }}</strong>
                <span>{{ log.objectType }} {{ log.objectId }}：{{ log.beforeStatus }} → {{ log.afterStatus }}</span>
              </div>
              <span>{{ dateTime(log.createdAt) }}</span>
            </article>
            <div v-if="auditLogs.length === 0" class="empty-state">暂无审计日志</div>
          </div>
          <nav v-if="totalPages(auditPager) > 1" class="pager" aria-label="审计日志分页">
            <button type="button" :disabled="auditPage.page === 0" @click="setLocalPage(auditPage, auditLogs.length, auditPage.page - 1)">‹</button>
            <button
              v-for="pageNo in visiblePageNumbers(auditPager)"
              :key="pageNo"
              type="button"
              :class="{ active: auditPage.page === pageNo }"
              @click="setLocalPage(auditPage, auditLogs.length, pageNo)"
            >
              {{ pageNo + 1 }}
            </button>
            <button type="button" :disabled="auditPage.page >= totalPages(auditPager) - 1" @click="setLocalPage(auditPage, auditLogs.length, auditPage.page + 1)">›</button>
          </nav>
        </section>
      </div>

      <section class="panel">
        <div class="panel-heading">
          <h2>最终一致性与库存占用</h2>
          <span>{{ snapshot?.outboxEvents?.length || 0 }} 个事件</span>
        </div>
        <div class="system-grid">
          <article>
            <h3>Outbox</h3>
            <p v-for="event in snapshot?.outboxEvents?.slice(0, 8)" :key="event.id">
              {{ event.eventType }} / {{ event.aggregateType }} {{ event.aggregateId }} / {{ event.status }}
            </p>
          </article>
          <article>
            <h3>库存占用</h3>
            <p v-for="reservation in snapshot?.reservations?.slice(0, 8)" :key="reservation.id">
              {{ reservation.productName }} × {{ reservation.quantity }} / {{ reservation.status }}
            </p>
          </article>
        </div>
      </section>
    </template>

    <div v-if="addressDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">收货地址</p>
            <h2>{{ addressDialog.mode === 'edit' ? '编辑地址' : '新增地址' }}</h2>
          </div>
          <button type="button" @click="addressDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>收货人</span>
          <input v-model="addressDialog.receiver" />
        </label>
        <label class="field">
          <span>手机号</span>
          <input v-model="addressDialog.phone" />
        </label>
        <label class="field">
          <span>详细地址</span>
          <textarea v-model="addressDialog.detail" rows="3"></textarea>
        </label>
        <div class="form-grid modal-form-grid">
          <label class="field">
            <span>距离 km</span>
            <input v-model.number="addressDialog.distanceKm" type="number" min="0.1" step="0.1" />
          </label>
          <label class="field check-field">
            <input v-model="addressDialog.inRange" type="checkbox" />
            <span>可配送</span>
          </label>
          <label class="field check-field">
            <input v-model="addressDialog.defaultAddress" type="checkbox" />
            <span>设为默认</span>
          </label>
        </div>
        <div class="button-row modal-actions">
          <button type="button" @click="addressDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitAddress">保存地址</button>
        </div>
      </section>
    </div>

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

    <div v-if="refundDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">退款申请</p>
            <h2>{{ refundDialog.order?.orderNo }}</h2>
          </div>
          <button type="button" @click="refundDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>退款原因</span>
          <select v-model="refundDialog.reason">
            <option>配送时间过长</option>
            <option>商家缺货</option>
            <option>商品错漏</option>
            <option>临时不需要</option>
          </select>
        </label>
        <label class="field">
          <span>问题说明</span>
          <textarea v-model="refundDialog.detail" rows="4"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="refundDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitRefund">提交退款</button>
        </div>
      </section>
    </div>

    <div v-if="reviewDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">订单评价</p>
            <h2>{{ reviewDialog.order?.orderNo }}</h2>
          </div>
          <button type="button" @click="reviewDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>评分</span>
          <input v-model.number="reviewDialog.score" type="number" min="1" max="5" />
        </label>
        <label class="field">
          <span>评价内容</span>
          <textarea v-model="reviewDialog.content" rows="4"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="reviewDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitReview">提交评价</button>
        </div>
      </section>
    </div>

    <div v-if="rejectOrderDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">拒单退款</p>
            <h2>{{ rejectOrderDialog.order?.orderNo }}</h2>
          </div>
          <button type="button" @click="rejectOrderDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>拒单原因</span>
          <select v-model="rejectOrderDialog.reason">
            <option>高峰期爆单，预计无法按时出餐</option>
            <option>核心商品临时售罄</option>
            <option>门店暂停营业</option>
            <option>配送范围异常</option>
          </select>
        </label>
        <label class="field">
          <span>处理说明</span>
          <textarea v-model="rejectOrderDialog.detail" rows="4"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="rejectOrderDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitRejectOrder">确认拒单并退款</button>
        </div>
      </section>
    </div>

    <div v-if="riderExceptionDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">配送异常上报</p>
            <h2>任务 {{ riderExceptionDialog.task?.id }}</h2>
          </div>
          <button type="button" @click="riderExceptionDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>异常类型</span>
          <select v-model="riderExceptionDialog.type">
            <option>联系不上用户</option>
            <option>地址无法进入</option>
            <option>商家未出餐</option>
            <option>餐品撒漏破损</option>
            <option>交通管制延误</option>
          </select>
        </label>
        <label class="field">
          <span>异常说明</span>
          <textarea v-model="riderExceptionDialog.detail" rows="4"></textarea>
        </label>
        <label class="field">
          <span>凭证编号</span>
          <input v-model="riderExceptionDialog.evidenceNo" />
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="riderExceptionDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitRiderException">上报平台处理</button>
        </div>
      </section>
    </div>

    <div v-if="ticketDialog.open" class="modal-backdrop" role="dialog" aria-modal="true">
      <section class="modal-card">
        <div class="modal-heading">
          <div>
            <p class="eyebrow">售后工单处理</p>
            <h2>工单 {{ ticketDialog.ticket?.id }}</h2>
          </div>
          <button type="button" @click="ticketDialog.open = false">关闭</button>
        </div>
        <label class="field">
          <span>处理结论</span>
          <textarea v-model="ticketDialog.result" rows="5"></textarea>
        </label>
        <div class="button-row modal-actions">
          <button type="button" @click="ticketDialog.open = false">取消</button>
          <button class="primary" type="button" @click="submitTicketDecision">提交处理</button>
        </div>
      </section>
    </div>
  </section>
</template>
