import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import CustomerRegisterView from '../views/CustomerRegisterView.vue'
import LoginView from '../views/LoginView.vue'
import OnboardingApplyView from '../views/OnboardingApplyView.vue'
import AdminCouponsView from '../views/admin/AdminCouponsView.vue'
import AdminMerchantAccountsView from '../views/admin/AdminMerchantAccountsView.vue'
import AdminOnboardingView from '../views/admin/AdminOnboardingView.vue'
import CustomerAddressesView from '../views/customer/CustomerAddressesView.vue'
import CustomerAfterSalesView from '../views/customer/CustomerAfterSalesView.vue'
import CustomerCheckoutView from '../views/customer/CustomerCheckoutView.vue'
import CustomerCouponsView from '../views/customer/CustomerCouponsView.vue'
import CustomerHomeView from '../views/customer/CustomerHomeView.vue'
import CustomerOrderDetailView from '../views/customer/CustomerOrderDetailView.vue'
import CustomerOrdersView from '../views/customer/CustomerOrdersView.vue'
import CustomerProfileView from '../views/customer/CustomerProfileView.vue'
import CustomerReviewsView from '../views/customer/CustomerReviewsView.vue'
import CustomerStoreDetailView from '../views/customer/CustomerStoreDetailView.vue'
import MerchantAfterSalesView from '../views/merchant/MerchantAfterSalesView.vue'
import MerchantDashboardView from '../views/merchant/MerchantDashboardView.vue'
import MerchantOrdersView from '../views/merchant/MerchantOrdersView.vue'
import MerchantProductsView from '../views/merchant/MerchantProductsView.vue'
import MerchantReviewsView from '../views/merchant/MerchantReviewsView.vue'
import MerchantStoreSettingsView from '../views/merchant/MerchantStoreSettingsView.vue'
import { getSession, roleHome, roleLogin, type Role } from '@/services/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: () => {
        const session = getSession('CUSTOMER') || getSession('MERCHANT') || getSession('RIDER') || getSession('ADMIN')
        return session ? roleHome(session.role) : '/customer/login'
      },
    },
    {
      path: '/login',
      redirect: '/customer/login',
    },
    {
      path: '/customer/login',
      name: 'customer-login',
      component: LoginView,
      meta: { public: true, portal: 'CUSTOMER' },
    },
    {
      path: '/merchant/login',
      name: 'merchant-login',
      component: LoginView,
      meta: { public: true, portal: 'MERCHANT' },
    },
    {
      path: '/rider/login',
      name: 'rider-login',
      component: LoginView,
      meta: { public: true, portal: 'RIDER' },
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: LoginView,
      meta: { public: true, portal: 'ADMIN' },
    },
    {
      path: '/register',
      alias: '/customer/register',
      name: 'customer-register',
      component: CustomerRegisterView,
      meta: { public: true },
    },
    {
      path: '/onboarding',
      name: 'onboarding-apply',
      component: OnboardingApplyView,
      meta: { public: true },
    },
    {
      path: '/customer',
      redirect: '/customer/home',
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/home',
      name: 'customer-home',
      component: CustomerHomeView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/stores/:id',
      name: 'customer-store-detail',
      component: CustomerStoreDetailView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/checkout',
      name: 'customer-checkout',
      component: CustomerCheckoutView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/orders',
      name: 'customer-orders',
      component: CustomerOrdersView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/orders/:id',
      name: 'customer-order-detail',
      component: CustomerOrderDetailView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/profile',
      name: 'customer-profile',
      component: CustomerProfileView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/addresses',
      name: 'customer-addresses',
      component: CustomerAddressesView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/coupons',
      name: 'customer-coupons',
      component: CustomerCouponsView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/reviews',
      name: 'customer-reviews',
      component: CustomerReviewsView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/customer/after-sales',
      name: 'customer-after-sales',
      component: CustomerAfterSalesView,
      meta: { role: 'CUSTOMER' },
    },
    {
      path: '/merchant',
      redirect: '/merchant/dashboard',
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/dashboard',
      name: 'merchant-dashboard',
      component: MerchantDashboardView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/products',
      name: 'merchant-products',
      component: MerchantProductsView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/orders',
      name: 'merchant-orders',
      component: MerchantOrdersView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/reviews',
      name: 'merchant-reviews',
      component: MerchantReviewsView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/store-settings',
      name: 'merchant-store-settings',
      component: MerchantStoreSettingsView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/merchant/after-sales',
      name: 'merchant-after-sales',
      component: MerchantAfterSalesView,
      meta: { role: 'MERCHANT' },
    },
    {
      path: '/rider',
      name: 'rider',
      component: DashboardView,
      meta: { role: 'RIDER' },
    },
    {
      path: '/admin',
      name: 'admin',
      component: DashboardView,
      meta: { role: 'ADMIN' },
    },
    {
      path: '/admin/merchant-accounts',
      name: 'admin-merchant-accounts',
      component: AdminMerchantAccountsView,
      meta: { role: 'ADMIN' },
    },
    {
      path: '/admin/onboarding-applications',
      alias: '/admin/onboarding',
      name: 'admin-onboarding-applications',
      component: AdminOnboardingView,
      meta: { role: 'ADMIN' },
    },
    {
      path: '/admin/coupons',
      name: 'admin-coupons',
      component: AdminCouponsView,
      meta: { role: 'ADMIN' },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  const requiredRole = to.meta.role as Role | undefined
  if (!requiredRole) {
    return true
  }
  const session = getSession(requiredRole)
  if (!session) {
    return { path: roleLogin(requiredRole), query: { redirect: to.fullPath } }
  }
  if (session.role !== requiredRole) {
    return { path: roleLogin(requiredRole), query: { redirect: to.fullPath } }
  }
  return true
})

export default router
