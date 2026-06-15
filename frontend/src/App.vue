<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const navItems = [
  { to: '/customer/home', label: '用户端', metric: '下单支付' },
  { to: '/merchant/dashboard', label: '商家端', metric: '接单出餐' },
  { to: '/rider', label: '骑手端', metric: '取餐送达' },
  { to: '/admin', label: '后台端', metric: '售后治理' },
]
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/takeout-flow.svg" alt="" />
        <div>
          <strong>类美团外卖系统</strong>
          <span>订单履约工作台</span>
        </div>
      </div>

      <nav class="side-nav">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          <span>{{ item.label }}</span>
          <small>{{ item.metric }}</small>
        </RouterLink>
      </nav>
    </aside>

    <main class="main-panel">
      <RouterView />
    </main>
  </div>
</template>
