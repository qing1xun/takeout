export type AnyRecord = Record<string, any>

export function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

export function dateTime(value: string | undefined) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

export function statusText(status: string) {
  const names: Record<string, string> = {
    WAIT_PAY: '待支付',
    PAID_WAIT_ACCEPT: '待接单',
    PREPARING: '备餐中',
    WAIT_PICKUP: '待骑手接单',
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

export function couponStatusText(status: string) {
  const names: Record<string, string> = {
    UNUSED: '未使用',
    LOCKED: '已锁定',
    USED: '已使用',
    EXPIRED: '已过期',
  }
  return names[status] || status
}

export function ticketStatusText(status: string) {
  const names: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    APPROVED: '已通过',
    RESOLVED: '已处理',
    REJECTED: '已驳回',
  }
  return names[status] || status
}

export function orderCanRefund(status: string) {
  return !['CANCELLED', 'REFUNDED', 'REFUNDING'].includes(status)
}
