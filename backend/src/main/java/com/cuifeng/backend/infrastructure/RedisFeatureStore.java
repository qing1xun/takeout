package com.cuifeng.backend.infrastructure;

import com.cuifeng.backend.takeout.TakeoutService.OrderItem;
import com.cuifeng.backend.takeout.TakeoutService.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class RedisFeatureStore {
    private static final String STOCK_SCRIPT = """
            for i = 1, #KEYS do
              local current = tonumber(redis.call('GET', KEYS[i]) or '-1')
              local quantity = tonumber(ARGV[i])
              if current < quantity then
                return 0
              end
            end
            for i = 1, #KEYS do
              redis.call('DECRBY', KEYS[i], tonumber(ARGV[i]))
            end
            return 1
            """;

    private static final String COUPON_SCRIPT = """
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
              return -1
            end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then
              return 0
            end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return stock - 1
            """;

    private static final String RATE_LIMIT_SCRIPT = """
            local now = tonumber(ARGV[1])
            local windowStart = now - tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, windowStart)
            local current = redis.call('ZCARD', KEYS[1])
            if current >= limit then
              return 0
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('EXPIRE', KEYS[1], math.ceil(tonumber(ARGV[2]) / 1000) + 1)
            return 1
            """;

    private final boolean enabled;
    private final JedisPool pool;

    public RedisFeatureStore(@Value("${app.redis.enabled:false}") boolean enabled,
                             @Value("${app.redis.host:localhost}") String host,
                             @Value("${app.redis.port:6379}") int port,
                             @Value("${app.redis.password:}") String password,
                             @Value("${app.redis.timeout-ms:2000}") int timeoutMs) {
        this.enabled = enabled;
        if (enabled) {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(64);
            config.setMaxIdle(16);
            config.setMinIdle(4);
            this.pool = password == null || password.isBlank()
                    ? new JedisPool(config, host, port, timeoutMs)
                    : new JedisPool(config, host, port, timeoutMs, password);
        } else {
            this.pool = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void initializeCatalog(Collection<Product> products) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            for (Product product : products) {
                jedis.set(stockKey(product.id), String.valueOf(product.stock));
            }
            jedis.setnx(couponStockKey("MEMBER"), "10000");
            jedis.setnx(couponStockKey("FAST"), "3000");
            jedis.setnx(couponStockKey("B2B"), "2000");
        }
    }

    public void initializeCouponStock(String batchCode, int stock) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.setnx(couponStockKey(batchCode), String.valueOf(stock));
        }
    }

    public boolean reserveStockBatch(List<OrderItem> items) {
        if (!enabled) {
            return true;
        }
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (OrderItem item : items) {
            keys.add(stockKey(item.productId));
            args.add(String.valueOf(item.quantity));
        }
        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.eval(STOCK_SCRIPT, keys, args);
            return Objects.equals(Long.valueOf(1), result);
        }
    }

    public void releaseStock(long productId, int quantity) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.incrBy(stockKey(productId), quantity);
        }
    }

    public CouponClaimResult claimCoupon(long userId, String sceneCode) {
        if (!enabled) {
            return CouponClaimResult.UNSUPPORTED;
        }
        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.eval(COUPON_SCRIPT,
                    List.of(couponStockKey(sceneCode), couponClaimedKey(sceneCode)),
                    List.of(String.valueOf(userId)));
            long value = result instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(result));
            if (value == -1) {
                return CouponClaimResult.alreadyClaimed(remainingCouponStock(sceneCode));
            }
            if (value == 0) {
                return CouponClaimResult.soldOut();
            }
            return CouponClaimResult.success(value);
        }
    }

    public boolean userClaimedCoupon(long userId, String sceneCode) {
        if (!enabled) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.sismember(couponClaimedKey(sceneCode), String.valueOf(userId));
        }
    }

    public long remainingCouponStock(String sceneCode) {
        if (!enabled) {
            return -1;
        }
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(couponStockKey(sceneCode));
            return value == null ? 0 : Long.parseLong(value);
        }
    }

    public boolean allowSlidingWindow(String key, int limit, int windowMs) {
        if (!enabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.eval(RATE_LIMIT_SCRIPT,
                    List.of("takeout:rate:" + key),
                    List.of(String.valueOf(now), String.valueOf(windowMs), String.valueOf(limit), now + "-" + UUID.randomUUID()));
            return Objects.equals(Long.valueOf(1), result);
        }
    }

    private String stockKey(long productId) {
        return "takeout:stock:" + productId;
    }

    private String couponStockKey(String sceneCode) {
        return "takeout:coupon:" + sceneCode + ":stock";
    }

    private String couponClaimedKey(String sceneCode) {
        return "takeout:coupon:" + sceneCode + ":claimed";
    }

    public record CouponClaimResult(String status, long remainingStock) {
        public static final CouponClaimResult UNSUPPORTED = new CouponClaimResult("UNSUPPORTED", -1);

        static CouponClaimResult success(long remainingStock) {
            return new CouponClaimResult("SUCCESS", remainingStock);
        }

        static CouponClaimResult alreadyClaimed(long remainingStock) {
            return new CouponClaimResult("ALREADY_CLAIMED", remainingStock);
        }

        static CouponClaimResult soldOut() {
            return new CouponClaimResult("SOLD_OUT", 0);
        }
    }
}
