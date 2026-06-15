package com.cuifeng.backend.order;

import com.cuifeng.backend.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

public class OrderStateMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            entry(OrderStatus.WAIT_PAY, Set.of(OrderStatus.PAID_WAIT_ACCEPT, OrderStatus.CANCELLED)),
            entry(OrderStatus.PAID_WAIT_ACCEPT, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED, OrderStatus.REFUNDING, OrderStatus.REFUNDED)),
            entry(OrderStatus.PREPARING, Set.of(OrderStatus.WAIT_PICKUP, OrderStatus.REFUNDING, OrderStatus.AFTERSALE)),
            entry(OrderStatus.WAIT_PICKUP, Set.of(OrderStatus.DELIVERING, OrderStatus.REFUNDING, OrderStatus.AFTERSALE)),
            entry(OrderStatus.DELIVERING, Set.of(OrderStatus.DELIVERED, OrderStatus.AFTERSALE)),
            entry(OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING, OrderStatus.AFTERSALE)),
            entry(OrderStatus.COMPLETED, Set.of(OrderStatus.REFUNDING, OrderStatus.AFTERSALE)),
            entry(OrderStatus.REFUNDING, Set.of(OrderStatus.REFUNDED, OrderStatus.AFTERSALE)),
            entry(OrderStatus.AFTERSALE, Set.of(OrderStatus.REFUNDING, OrderStatus.REFUNDED, OrderStatus.COMPLETED)),
            entry(OrderStatus.CANCELLED, Set.of()),
            entry(OrderStatus.REFUNDED, Set.of())
    );

    private OrderStateMachine() {
    }

    public static boolean canTransit(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertCanTransit(OrderStatus from, OrderStatus to) {
        if (!canTransit(from, to)) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
