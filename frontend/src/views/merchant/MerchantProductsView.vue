<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/services/api'
import { money, type AnyRecord } from '../customer/customerShared'

const products = ref<AnyRecord[]>([])
const page = ref({ page: 0, size: 10, total: 0, hasMore: false })
const notice = ref('')
const error = ref('')
const busy = ref(false)
const draft = ref({
  id: 0,
  name: '',
  description: '',
  price: '19.90',
  stock: 50,
  onSale: true,
  category: '热销',
  imageTone: 'default',
  discountLabel: '',
  originalPrice: '19.90',
})

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const pageNumbers = computed(() => Array.from({ length: Math.min(10, totalPages.value) }, (_, index) => index))

load(0)

async function load(pageNo = 0) {
  busy.value = true
  error.value = ''
  try {
    const result = await api<AnyRecord>('MERCHANT', `/merchant/products/page?page=${Math.max(0, pageNo)}&size=${page.value.size}`)
    products.value = result.records
    page.value = { page: result.page, size: result.size, total: result.total, hasMore: result.hasMore }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    busy.value = false
  }
}

function resetDraft() {
  draft.value = {
    id: 0,
    name: '',
    description: '',
    price: '19.90',
    stock: 50,
    onSale: true,
    category: '热销',
    imageTone: 'default',
    discountLabel: '',
    originalPrice: '19.90',
  }
}

function edit(product: AnyRecord) {
  draft.value = {
    id: product.id,
    name: product.name,
    description: product.description,
    price: String(product.price),
    stock: product.stock,
    onSale: product.onSale,
    category: product.category,
    imageTone: product.imageTone,
    discountLabel: product.discountLabel,
    originalPrice: String(product.originalPrice || product.price),
  }
}

async function save() {
  const path = draft.value.id ? `/merchant/products/${draft.value.id}` : '/merchant/products'
  await api<AnyRecord>('MERCHANT', path, {
    method: draft.value.id ? 'PATCH' : 'POST',
    body: { ...draft.value, id: draft.value.id || undefined },
  })
  notice.value = draft.value.id ? '商品已更新' : '商品已新增'
  resetDraft()
  await load(page.value.page)
}

async function toggleSale(product: AnyRecord) {
  await api<AnyRecord>('MERCHANT', `/merchant/products/${product.id}`, {
    method: 'PATCH',
    body: { ...product, onSale: !product.onSale, price: String(product.price), originalPrice: String(product.originalPrice || product.price) },
  })
  notice.value = product.onSale ? '商品已下架' : '商品已上架'
  await load(page.value.page)
}
</script>

<template>
  <section class="dashboard">
    <header class="topbar">
      <div>
        <p class="eyebrow">商家端 / 商品管理</p>
        <h1>商品新增、编辑、库存和上下架</h1>
      </div>
      <RouterLink class="button-link" to="/merchant/dashboard">返回概览</RouterLink>
    </header>

    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="busy" class="loading">加载中...</div>

    <div class="detail-layout">
      <section class="panel">
        <div class="panel-heading">
          <h2>商品列表</h2>
          <span>{{ products.length }} / {{ page.total }} 个</span>
        </div>
        <div class="data-list">
          <article v-for="product in products" :key="product.id" class="compact-row">
            <div>
              <strong>{{ product.name }} · {{ product.category }}</strong>
              <span>{{ money(product.price) }} / 库存 {{ product.stock }} / {{ product.onSale ? '销售中' : '已下架' }}</span>
            </div>
            <div class="row-actions">
              <button type="button" @click="edit(product)">编辑</button>
              <button type="button" @click="toggleSale(product)">{{ product.onSale ? '下架' : '上架' }}</button>
            </div>
          </article>
          <div v-if="products.length === 0" class="empty-state">暂无商品</div>
        </div>
        <nav v-if="totalPages > 1" class="pager" aria-label="商品分页">
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

      <section class="panel">
        <div class="panel-heading">
          <h2>{{ draft.id ? '编辑商品' : '新增商品' }}</h2>
          <button type="button" @click="resetDraft">清空</button>
        </div>
        <label class="field">
          <span>商品名</span>
          <input v-model="draft.name" />
        </label>
        <label class="field">
          <span>商品描述</span>
          <textarea v-model="draft.description"></textarea>
        </label>
        <label class="field">
          <span>价格</span>
          <input v-model="draft.price" />
        </label>
        <label class="field">
          <span>原价</span>
          <input v-model="draft.originalPrice" />
        </label>
        <label class="field">
          <span>库存</span>
          <input v-model.number="draft.stock" type="number" min="0" />
        </label>
        <label class="field">
          <span>分类</span>
          <input v-model="draft.category" />
        </label>
        <label class="field">
          <span>图片色调</span>
          <select v-model="draft.imageTone">
            <option value="default">默认</option>
            <option value="spicy">辣味</option>
            <option value="fresh">清爽</option>
            <option value="drink">饮品</option>
            <option value="market">商超</option>
          </select>
        </label>
        <label class="field">
          <span>优惠标签</span>
          <input v-model="draft.discountLabel" />
        </label>
        <label class="field check-field">
          <input v-model="draft.onSale" type="checkbox" />
          <span>上架销售</span>
        </label>
        <button class="primary" type="button" @click="save">保存商品</button>
      </section>
    </div>
  </section>
</template>
