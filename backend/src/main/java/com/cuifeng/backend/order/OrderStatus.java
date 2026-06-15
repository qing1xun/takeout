package com.cuifeng.backend.order;

public enum OrderStatus {
    WAIT_PAY,
    PAID_WAIT_ACCEPT,
    PREPARING,
    WAIT_PICKUP,
    DELIVERING,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDING,
    REFUNDED,
    AFTERSALE
}
