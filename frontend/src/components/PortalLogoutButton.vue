<script setup lang="ts">
import { useRouter } from 'vue-router'
import { logout, logoutAll, roleLogin, type Role } from '@/services/api'

const props = defineProps<{
  role: Role
}>()

const router = useRouter()

async function handleLogout() {
  await logout(props.role)
  await router.replace(roleLogin(props.role))
}

async function handleLogoutAll() {
  await logoutAll(props.role)
  await router.replace(roleLogin(props.role))
}
</script>

<template>
  <button type="button" @click="handleLogout">退出当前端</button>
  <button type="button" @click="handleLogoutAll">退出全部设备</button>
</template>
