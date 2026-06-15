package com.cuifeng.backend.takeout;

import com.cuifeng.backend.auth.Role;
import com.cuifeng.backend.order.OrderStatus;
import com.cuifeng.backend.takeout.TakeoutService.Account;
import com.cuifeng.backend.takeout.TakeoutService.Address;
import com.cuifeng.backend.takeout.TakeoutService.AddressSnapshot;
import com.cuifeng.backend.takeout.TakeoutService.AuditLog;
import com.cuifeng.backend.takeout.TakeoutService.Banner;
import com.cuifeng.backend.takeout.TakeoutService.CartItem;
import com.cuifeng.backend.takeout.TakeoutService.Channel;
import com.cuifeng.backend.takeout.TakeoutService.CouponActivity;
import com.cuifeng.backend.takeout.TakeoutService.CouponStatusLog;
import com.cuifeng.backend.takeout.TakeoutService.CouponTemplate;
import com.cuifeng.backend.takeout.TakeoutService.DeliveryOrder;
import com.cuifeng.backend.takeout.TakeoutService.DeliveryStatusLog;
import com.cuifeng.backend.takeout.TakeoutService.FulfillmentStep;
import com.cuifeng.backend.takeout.TakeoutService.InventoryReservation;
import com.cuifeng.backend.takeout.TakeoutService.Order;
import com.cuifeng.backend.takeout.TakeoutService.OrderItem;
import com.cuifeng.backend.takeout.TakeoutService.OrderStatusRecord;
import com.cuifeng.backend.takeout.TakeoutService.OnboardingApplication;
import com.cuifeng.backend.takeout.TakeoutService.OutboxEvent;
import com.cuifeng.backend.takeout.TakeoutService.OutboxConsumeRecord;
import com.cuifeng.backend.takeout.TakeoutService.PaymentOrder;
import com.cuifeng.backend.takeout.TakeoutService.Product;
import com.cuifeng.backend.takeout.TakeoutService.RefundOrder;
import com.cuifeng.backend.takeout.TakeoutService.Review;
import com.cuifeng.backend.takeout.TakeoutService.RiskRecord;
import com.cuifeng.backend.takeout.TakeoutService.Store;
import com.cuifeng.backend.takeout.TakeoutService.Ticket;
import com.cuifeng.backend.takeout.TakeoutService.UserCoupon;
import com.cuifeng.backend.takeout.TakeoutService.MerchantNotification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseStore {
    private final String persistence;
    private final String url;
    private final String username;
    private final String password;
    private final DataSource dataSource;

    public DatabaseStore(@Value("${app.persistence:memory}") String persistence,
                         @Value("${app.datasource.url:}") String url,
                         @Value("${app.datasource.username:}") String username,
                         @Value("${app.datasource.password:}") String password,
                         ObjectProvider<DataSource> dataSourceProvider) {
        this.persistence = persistence;
        this.url = url;
        this.username = username;
        this.password = password;
        this.dataSource = dataSourceProvider.getIfAvailable();
    }

    public boolean enabled() {
        return "mysql".equalsIgnoreCase(persistence);
    }

    public void initializeSchema() {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            executeScript(connection, "db/mysql/001_schema.sql");
            migrateSchema(connection);
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 MySQL schema 失败", ex);
        }
    }

    public void bootstrapCatalog(Collection<Account> accounts,
                                 Collection<Address> addresses,
                                 List<Channel> channels,
                                 List<Banner> banners,
                                 Collection<Store> stores,
                                 Collection<Product> products) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            for (Account account : accounts) {
                update(connection, """
                        INSERT INTO user_account
                        (id, username, password_hash, display_name, role, store_id, phone)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                        username = VALUES(username), password_hash = VALUES(password_hash), display_name = VALUES(display_name),
                        role = VALUES(role), store_id = VALUES(store_id), phone = VALUES(phone)
                        """, ps -> {
                    ps.setLong(1, account.id);
                    ps.setString(2, account.username);
                    ps.setString(3, account.password);
                    ps.setString(4, account.displayName);
                    ps.setString(5, account.role.name());
                    setNullableLong(ps, 6, account.storeId);
                    ps.setString(7, account.phone);
                });
            }
            for (Address address : addresses) {
                update(connection, """
                        INSERT IGNORE INTO user_address
                        (id, user_id, receiver, phone_masked, detail_masked, distance_km, in_range, default_address)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, ps -> {
                    ps.setLong(1, address.id);
                    ps.setLong(2, address.userId);
                    ps.setString(3, address.receiver);
                    ps.setString(4, address.phoneMasked);
                    ps.setString(5, address.detailMasked);
                    ps.setDouble(6, address.distanceKm);
                    ps.setBoolean(7, address.inRange);
                    ps.setBoolean(8, address.defaultAddress);
                });
            }
            int sortNo = 0;
            for (Channel channel : channels) {
                int currentSort = sortNo++;
                update(connection, """
                        INSERT IGNORE INTO channel (code, name, subtitle, sort_no)
                        VALUES (?, ?, ?, ?)
                        """, ps -> {
                    ps.setString(1, channel.code());
                    ps.setString(2, channel.name());
                    ps.setString(3, channel.subtitle());
                    ps.setInt(4, currentSort);
                });
            }
            sortNo = 0;
            for (Banner banner : banners) {
                int currentSort = sortNo++;
                update(connection, """
                        INSERT IGNORE INTO banner (scene_code, title, subtitle, action_text, sort_no)
                        VALUES (?, ?, ?, ?, ?)
                        """, ps -> {
                    ps.setString(1, banner.sceneCode());
                    ps.setString(2, banner.title());
                    ps.setString(3, banner.subtitle());
                    ps.setString(4, banner.actionText());
                    ps.setInt(5, currentSort);
                });
            }
            for (Store store : stores) {
                upsertStore(connection, store);
                insertStoreChildren(connection, store);
            }
            for (Product product : products) {
                insertProductIgnore(connection, product);
            }
            connection.commit();
        } catch (Exception ex) {
            throw new IllegalStateException("写入 MySQL 种子数据失败", ex);
        }
    }

    public void bootstrapCouponActivities(Collection<CouponActivity> activities) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            for (CouponActivity activity : activities) {
                update(connection, """
                        INSERT INTO coupon_activity
                        (batch_code, scene_code, title, subtitle, start_at, end_at, per_user_limit, stock)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE title = VALUES(title), subtitle = VALUES(subtitle),
                        start_at = VALUES(start_at), end_at = VALUES(end_at), per_user_limit = VALUES(per_user_limit),
                        stock = VALUES(stock)
                        """, ps -> {
                    ps.setString(1, activity.batchCode);
                    ps.setString(2, activity.sceneCode);
                    ps.setString(3, activity.title);
                    ps.setString(4, activity.subtitle);
                    setTimestamp(ps, 5, activity.startAt);
                    setTimestamp(ps, 6, activity.endAt);
                    ps.setInt(7, activity.perUserLimit);
                    ps.setInt(8, activity.stock);
                });
                int sortNo = 0;
                for (CouponTemplate template : activity.templates) {
                    int currentSort = sortNo++;
                    update(connection, """
                            INSERT INTO coupon_template
                            (batch_code, template_code, title, discount_amount, threshold_amount, scope, sort_no)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE title = VALUES(title), discount_amount = VALUES(discount_amount),
                            threshold_amount = VALUES(threshold_amount), scope = VALUES(scope), sort_no = VALUES(sort_no)
                            """, ps -> {
                        ps.setString(1, activity.batchCode);
                        ps.setString(2, template.templateCode);
                        ps.setString(3, template.title);
                        ps.setBigDecimal(4, template.discountAmount);
                        ps.setBigDecimal(5, template.thresholdAmount);
                        ps.setString(6, template.scope);
                        ps.setInt(7, currentSort);
                    });
                }
            }
            connection.commit();
        } catch (Exception ex) {
            throw new IllegalStateException("写入 MySQL 优惠券活动失败", ex);
        }
    }

    public CatalogState loadCatalog() {
        CatalogState state = new CatalogState();
        if (!enabled()) {
            return state;
        }
        try (Connection connection = connection()) {
            query(connection, "SELECT * FROM user_account ORDER BY id", rs ->
                    state.accounts.add(new Account(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("display_name"),
                            Role.valueOf(rs.getString("role")),
                            nullableLong(rs, "store_id"),
                            rs.getString("phone"))));
            query(connection, "SELECT * FROM user_address ORDER BY id", rs ->
                    state.addresses.add(new Address(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("receiver"),
                            rs.getString("phone_masked"),
                            rs.getString("detail_masked"),
                            rs.getDouble("distance_km"),
                            rs.getBoolean("in_range"),
                            rs.getBoolean("default_address"))));
            query(connection, "SELECT * FROM channel ORDER BY sort_no, code", rs ->
                    state.channels.add(new Channel(rs.getString("name"), rs.getString("subtitle"), rs.getString("code"))));
            query(connection, "SELECT * FROM banner ORDER BY sort_no, scene_code", rs ->
                    state.banners.add(new Banner(rs.getString("title"), rs.getString("subtitle"), rs.getString("action_text"), rs.getString("scene_code"))));
            query(connection, "SELECT * FROM store ORDER BY id", rs ->
                    state.stores.add(new Store(
                            rs.getLong("id"),
                            rs.getLong("merchant_id"),
                            rs.getString("name"),
                            rs.getString("notice"),
                            rs.getBoolean("open_flag"),
                            rs.getBigDecimal("min_delivery_amount"),
                            rs.getBigDecimal("delivery_fee"),
                            rs.getDouble("rating"),
                            loadStringList(connection, "SELECT tag FROM store_tag WHERE store_id = ? ORDER BY tag", rs.getLong("id")),
                            rs.getString("category"),
                            rs.getString("area"),
                            rs.getString("logo_text"),
                            rs.getInt("avg_delivery_minutes"),
                            rs.getDouble("distance_km"),
                            rs.getInt("monthly_sales"),
                            rs.getString("status_message"),
                            rs.getString("delivery_guarantee"),
                            loadStringList(connection, "SELECT promotion FROM store_promotion WHERE store_id = ? ORDER BY sort_no, promotion", rs.getLong("id")),
                            loadStringList(connection, "SELECT coupon_hint FROM store_coupon_hint WHERE store_id = ? ORDER BY sort_no, coupon_hint", rs.getLong("id")),
                            rs.getInt("delivery_priority"),
                            rs.getString("business_hours"),
                            rs.getDouble("delivery_range_km"))));
            query(connection, "SELECT * FROM product ORDER BY id", rs ->
                    state.products.add(new Product(
                            rs.getLong("id"),
                            rs.getLong("store_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBigDecimal("price"),
                            rs.getInt("stock"),
                            rs.getBoolean("on_sale"),
                            rs.getInt("monthly_sales"),
                            rs.getString("category"),
                            rs.getString("image_tone"),
                            rs.getInt("ranking"),
                            rs.getString("discount_label"),
                            rs.getBigDecimal("original_price"))));
            return state;
        } catch (Exception ex) {
            throw new IllegalStateException("从 MySQL 加载基础数据失败", ex);
        }
    }

    public TransactionState loadTransactions() {
        TransactionState state = new TransactionState();
        if (!enabled()) {
            return state;
        }
        try (Connection connection = connection()) {
            query(connection, "SELECT * FROM order_main ORDER BY created_at", rs -> {
                Order order = new Order();
                order.id = rs.getLong("id");
                order.orderNo = rs.getString("order_no");
                order.customerId = rs.getLong("customer_id");
                order.customerName = rs.getString("customer_name");
                order.storeId = rs.getLong("store_id");
                order.storeName = rs.getString("store_name");
                order.status = OrderStatus.valueOf(rs.getString("status"));
                order.itemAmount = rs.getBigDecimal("item_amount");
                order.deliveryFee = rs.getBigDecimal("delivery_fee");
                order.discountAmount = rs.getBigDecimal("discount_amount");
                order.payAmount = rs.getBigDecimal("pay_amount");
                order.idempotencyToken = rs.getString("idempotency_token");
                order.couponCode = rs.getString("coupon_code");
                order.paymentOrderNo = rs.getString("payment_order_no");
                order.deliveryId = nullableLong(rs, "delivery_id");
                order.refundId = nullableLong(rs, "refund_id");
                order.addressSnapshot = new AddressSnapshot(
                        rs.getString("receiver"),
                        rs.getString("phone_masked"),
                        rs.getString("detail_masked"),
                        rs.getDouble("distance_km"));
                order.estimatedDeliveryMinutes = rs.getInt("estimated_delivery_minutes");
                order.createdAt = ldt(rs, "created_at");
                order.paidAt = ldt(rs, "paid_at");
                order.finishedAt = ldt(rs, "finished_at");
                order.items.addAll(loadOrderItems(connection, order.id));
                order.statusRecords.addAll(loadStatusRecords(connection, order.id));
                order.fulfillmentSteps.addAll(loadFulfillmentSteps(connection, order.id));
                order.review = loadReview(connection, order.id);
                state.orders.put(order.id, order);
                state.idempotentOrders.put(order.customerId + ":" + order.idempotencyToken, order.id);
            });
            query(connection, "SELECT * FROM payment_order ORDER BY id", rs ->
                    state.payments.put(rs.getLong("id"), new PaymentOrder(
                            rs.getLong("id"),
                            rs.getString("payment_no"),
                            rs.getLong("order_id"),
                            rs.getString("status"),
                            rs.getBigDecimal("amount"),
                            rs.getString("callback_flow_no"),
                            ldt(rs, "paid_at"))));
            query(connection, "SELECT * FROM cart_item ORDER BY user_id, updated_at, id", rs ->
                    state.carts.computeIfAbsent(rs.getLong("user_id"), ignored -> new ArrayList<>())
                            .add(new CartItem(
                                    rs.getLong("id"),
                                    rs.getLong("product_id"),
                                    rs.getLong("store_id"),
                                    rs.getString("product_name"),
                                    rs.getBigDecimal("price"),
                                    rs.getInt("quantity"))));
            query(connection, "SELECT * FROM refund_order ORDER BY id", rs ->
                    state.refunds.put(rs.getLong("id"), new RefundOrder(
                            rs.getLong("id"),
                            rs.getString("refund_no"),
                            rs.getLong("order_id"),
                            rs.getString("status"),
                            rs.getBigDecimal("amount"),
                            rs.getString("reason"),
                            ldt(rs, "created_at"),
                            ldt(rs, "finished_at"))));
            query(connection, "SELECT * FROM delivery_order ORDER BY id", rs ->
                    state.deliveries.put(rs.getLong("id"), new DeliveryOrder(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            nullableLong(rs, "rider_id"),
                            rs.getString("rider_name"),
                            rs.getString("status"),
                            rs.getString("current_step"),
                            rs.getString("exception_reason"),
                            ldt(rs, "created_at"),
                            ldt(rs, "delivered_at"))));
            query(connection, "SELECT * FROM delivery_status_log ORDER BY created_at, id", rs -> {
                DeliveryStatusLog log = new DeliveryStatusLog(
                        rs.getLong("id"),
                        rs.getLong("delivery_id"),
                        rs.getLong("order_id"),
                        nullableLong(rs, "rider_id"),
                        rs.getString("rider_name"),
                        rs.getString("before_status"),
                        rs.getString("after_status"),
                        rs.getString("operator_name"),
                        rs.getString("detail"),
                        rs.getString("location"),
                        ldt(rs, "created_at"));
                state.deliveryStatusLogs.put(log.id, log);
                DeliveryOrder delivery = state.deliveries.get(log.deliveryId);
                if (delivery != null) {
                    delivery.statusLogs.add(log);
                }
            });
            query(connection, "SELECT * FROM inventory_reservation ORDER BY id", rs ->
                    state.reservations.put(rs.getLong("id"), new InventoryReservation(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getLong("product_id"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getString("status"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM customer_service_ticket ORDER BY id", rs ->
                    state.tickets.put(rs.getLong("id"), new Ticket(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getLong("customer_id"),
                            rs.getString("status"),
                            rs.getString("reason"),
                            rs.getString("result"),
                            ldt(rs, "created_at"),
                            ldt(rs, "finished_at"))));
            query(connection, "SELECT * FROM review ORDER BY id", rs ->
                    state.reviews.put(rs.getLong("id"), new Review(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getLong("customer_id"),
                            rs.getInt("score"),
                            rs.getString("content"),
                            rs.getString("merchant_reply"),
                            ldt(rs, "merchant_replied_at"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM risk_record ORDER BY id", rs ->
                    state.riskRecords.put(rs.getLong("id"), new RiskRecord(
                            rs.getLong("id"),
                            rs.getString("type"),
                            rs.getString("object_type"),
                            rs.getLong("object_id"),
                            rs.getString("reason"),
                            rs.getString("status"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM audit_log ORDER BY id", rs ->
                    state.auditLogs.put(rs.getLong("id"), new AuditLog(
                            rs.getLong("id"),
                            rs.getLong("actor_id"),
                            rs.getString("actor_name"),
                            Role.valueOf(rs.getString("actor_role")),
                            rs.getString("action"),
                            rs.getString("object_type"),
                            rs.getLong("object_id"),
                            rs.getString("before_status"),
                            rs.getString("after_status"),
                            rs.getString("reason"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM outbox_event ORDER BY id", rs ->
                    state.outboxEvents.put(rs.getLong("id"), new OutboxEvent(
                            rs.getLong("id"),
                            rs.getString("event_type"),
                            rs.getString("aggregate_type"),
                            rs.getLong("aggregate_id"),
                            rs.getString("payload"),
                            rs.getString("status"),
                            rs.getInt("retry_count"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM outbox_consume_log ORDER BY consumed_at, event_id", rs ->
                    state.outboxConsumeRecords.put(rs.getString("consumer_name") + ":" + rs.getLong("event_id"),
                            new OutboxConsumeRecord(
                                    rs.getLong("event_id"),
                                    rs.getString("consumer_name"),
                                    rs.getString("event_type"),
                                    rs.getString("aggregate_type"),
                                    rs.getLong("aggregate_id"),
                                    rs.getString("status"),
                                    ldt(rs, "consumed_at"))));
            query(connection, "SELECT * FROM merchant_notification ORDER BY created_at, id", rs ->
                    state.merchantNotifications.put(rs.getLong("id"), new MerchantNotification(
                            rs.getLong("id"),
                            rs.getLong("event_id"),
                            rs.getLong("order_id"),
                            rs.getLong("store_id"),
                            rs.getString("title"),
                            rs.getString("message"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT callback_flow_no FROM payment_callback_flow", rs ->
                    state.paymentCallbackFlows.add(rs.getString("callback_flow_no")));
            query(connection, "SELECT * FROM user_coupon ORDER BY claimed_at, id", rs ->
                    state.userCoupons.put(rs.getLong("id"), new UserCoupon(
                            rs.getLong("id"),
                            rs.getString("coupon_code"),
                            rs.getLong("user_id"),
                            rs.getString("scene_code"),
                            rs.getString("batch_code"),
                            rs.getString("template_code"),
                            rs.getString("title"),
                            rs.getBigDecimal("discount_amount"),
                            rs.getBigDecimal("threshold_amount"),
                            rs.getString("scope"),
                            rs.getString("status"),
                            nullableLong(rs, "locked_order_id"),
                            nullableLong(rs, "used_order_id"),
                            rs.getString("reason"),
                            ldt(rs, "claimed_at"),
                            ldt(rs, "valid_from"),
                            ldt(rs, "valid_to"),
                            ldt(rs, "updated_at"))));
            query(connection, "SELECT * FROM coupon_status_log ORDER BY created_at, id", rs ->
                    state.couponStatusLogs.put(rs.getLong("id"), new CouponStatusLog(
                            rs.getLong("id"),
                            rs.getLong("coupon_id"),
                            rs.getString("coupon_code"),
                            rs.getLong("user_id"),
                            rs.getString("before_status"),
                            rs.getString("after_status"),
                            nullableLong(rs, "related_order_id"),
                            rs.getString("operator_name"),
                            rs.getString("reason"),
                            ldt(rs, "created_at"))));
            query(connection, "SELECT * FROM onboarding_application ORDER BY id", rs ->
                    state.onboardingApplications.put(rs.getLong("id"), new OnboardingApplication(
                            rs.getLong("id"),
                            Role.valueOf(rs.getString("role")),
                            rs.getString("status"),
                            rs.getString("applicant_name"),
                            rs.getString("phone"),
                            rs.getString("store_name"),
                            rs.getString("category"),
                            rs.getString("address"),
                            rs.getString("preferred_username"),
                            rs.getString("reason"),
                            rs.getString("result"),
                            nullableLong(rs, "created_account_id"),
                            nullableLong(rs, "created_store_id"),
                            ldt(rs, "created_at"),
                            ldt(rs, "finished_at"))));
            return state;
        } catch (Exception ex) {
            throw new IllegalStateException("从 MySQL 加载交易数据失败", ex);
        }
    }

    public long maxKnownId() {
        if (!enabled()) {
            return 0;
        }
        String[] tables = {
                "user_account", "user_address", "store", "product", "cart_item", "order_main", "order_status_record",
                "fulfillment_step", "inventory_reservation", "payment_order", "refund_order", "delivery_order",
                "delivery_status_log", "review", "customer_service_ticket", "risk_record", "audit_log", "outbox_event",
                "onboarding_application", "user_coupon", "coupon_status_log"
        };
        long max = 0;
        try (Connection connection = connection()) {
            for (String table : tables) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
                    if (rs.next()) {
                        max = Math.max(max, rs.getLong(1));
                    }
                }
            }
            return max;
        } catch (Exception ex) {
            throw new IllegalStateException("计算 MySQL 最大业务 ID 失败", ex);
        }
    }

    public void saveProduct(Product product) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO product
                    (id, store_id, name, description, price, stock, on_sale, monthly_sales, category, image_tone, ranking, discount_label, original_price)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    name = VALUES(name), description = VALUES(description), price = VALUES(price), stock = VALUES(stock),
                    on_sale = VALUES(on_sale), monthly_sales = VALUES(monthly_sales), category = VALUES(category),
                    image_tone = VALUES(image_tone), ranking = VALUES(ranking), discount_label = VALUES(discount_label),
                    original_price = VALUES(original_price)
                    """, ps -> bindProduct(ps, product));
        } catch (Exception ex) {
            throw new IllegalStateException("保存商品失败", ex);
        }
    }

    public void saveAccount(Account account) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO user_account
                    (id, username, password_hash, display_name, role, store_id, phone)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE username = VALUES(username), password_hash = VALUES(password_hash),
                    display_name = VALUES(display_name), role = VALUES(role), store_id = VALUES(store_id), phone = VALUES(phone)
                    """, ps -> {
                ps.setLong(1, account.id);
                ps.setString(2, account.username);
                ps.setString(3, account.password);
                ps.setString(4, account.displayName);
                ps.setString(5, account.role.name());
                setNullableLong(ps, 6, account.storeId);
                ps.setString(7, account.phone);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存账号失败", ex);
        }
    }

    public void saveOnboardingApplication(OnboardingApplication application) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO onboarding_application
                    (id, role, status, applicant_name, phone, store_name, category, address, preferred_username,
                     reason, result, created_account_id, created_store_id, created_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), result = VALUES(result),
                    created_account_id = VALUES(created_account_id), created_store_id = VALUES(created_store_id),
                    finished_at = VALUES(finished_at)
                    """, ps -> {
                ps.setLong(1, application.id);
                ps.setString(2, application.role.name());
                ps.setString(3, application.status);
                ps.setString(4, application.applicantName);
                ps.setString(5, application.phone);
                ps.setString(6, application.storeName);
                ps.setString(7, application.category);
                ps.setString(8, application.address);
                ps.setString(9, application.preferredUsername);
                ps.setString(10, application.reason);
                ps.setString(11, application.result);
                setNullableLong(ps, 12, application.createdAccountId);
                setNullableLong(ps, 13, application.createdStoreId);
                setTimestamp(ps, 14, application.createdAt);
                setTimestamp(ps, 15, application.finishedAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存入驻申请失败", ex);
        }
    }

    public void saveAddress(Address address) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO user_address
                    (id, user_id, receiver, phone_masked, detail_masked, distance_km, in_range, default_address)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE receiver = VALUES(receiver), phone_masked = VALUES(phone_masked),
                    detail_masked = VALUES(detail_masked), distance_km = VALUES(distance_km),
                    in_range = VALUES(in_range), default_address = VALUES(default_address)
                    """, ps -> {
                ps.setLong(1, address.id);
                ps.setLong(2, address.userId);
                ps.setString(3, address.receiver);
                ps.setString(4, address.phoneMasked);
                ps.setString(5, address.detailMasked);
                ps.setDouble(6, address.distanceKm);
                ps.setBoolean(7, address.inRange);
                ps.setBoolean(8, address.defaultAddress);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存收货地址失败", ex);
        }
    }

    public void deleteAddress(long addressId) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, "DELETE FROM user_address WHERE id = ?", ps -> ps.setLong(1, addressId));
        } catch (Exception ex) {
            throw new IllegalStateException("删除收货地址失败", ex);
        }
    }

    public void saveStore(Store store) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            upsertStore(connection, store);
            replaceStoreChildren(connection, store);
            connection.commit();
        } catch (Exception ex) {
            throw new IllegalStateException("保存门店失败", ex);
        }
    }

    public void saveCartItem(long userId, CartItem item) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO cart_item
                    (id, user_id, store_id, product_id, product_name, price, quantity, selected, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE store_id = VALUES(store_id), product_name = VALUES(product_name),
                    price = VALUES(price), quantity = VALUES(quantity), selected = TRUE, updated_at = CURRENT_TIMESTAMP
                    """, ps -> {
                ps.setLong(1, item.id);
                ps.setLong(2, userId);
                ps.setLong(3, item.storeId);
                ps.setLong(4, item.productId);
                ps.setString(5, item.productName);
                ps.setBigDecimal(6, item.price);
                ps.setInt(7, item.quantity);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存购物车失败", ex);
        }
    }

    public void deleteCartItem(long userId, long productId) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, "DELETE FROM cart_item WHERE user_id = ? AND product_id = ?", ps -> {
                ps.setLong(1, userId);
                ps.setLong(2, productId);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("删除购物车商品失败", ex);
        }
    }

    public void deleteCartItems(long userId) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, "DELETE FROM cart_item WHERE user_id = ?", ps -> ps.setLong(1, userId));
        } catch (Exception ex) {
            throw new IllegalStateException("清空购物车失败", ex);
        }
    }

    public boolean reserveStockBatch(List<OrderItem> items) {
        if (!enabled()) {
            return true;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            if (!reserveStockBatch(connection, items)) {
                connection.rollback();
                return false;
            }
            connection.commit();
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("批量扣减库存失败", ex);
        }
    }

    public boolean saveOrderCreatedTransaction(Order order,
                                               PaymentOrder payment,
                                               List<InventoryReservation> reservations,
                                               OutboxEvent outboxEvent,
                                               AuditLog auditLog) {
        if (!enabled()) {
            return true;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            if (!reserveStockBatch(connection, order.items)) {
                connection.rollback();
                return false;
            }
            saveOrderAggregate(connection, order);
            savePayment(connection, payment);
            for (InventoryReservation reservation : reservations) {
                saveReservation(connection, reservation);
            }
            saveOutbox(connection, outboxEvent);
            if (auditLog != null) {
                saveAudit(connection, auditLog);
            }
            connection.commit();
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("保存订单与 Outbox 事务失败", ex);
        }
    }

    public void releaseStock(long productId, int quantity) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, "UPDATE product SET stock = stock + ? WHERE id = ?", ps -> {
                ps.setInt(1, quantity);
                ps.setLong(2, productId);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("释放库存失败", ex);
        }
    }

    public void saveOrderAggregate(Order order) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            saveOrderAggregate(connection, order);
            connection.commit();
        } catch (Exception ex) {
            throw new IllegalStateException("保存订单聚合失败", ex);
        }
    }

    public void savePayment(PaymentOrder payment) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            savePayment(connection, payment);
        } catch (Exception ex) {
            throw new IllegalStateException("保存支付单失败", ex);
        }
    }

    public void savePaymentCallback(String callbackFlowNo, String paymentNo) {
        if (!enabled() || callbackFlowNo == null) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT IGNORE INTO payment_callback_flow (callback_flow_no, payment_no)
                    VALUES (?, ?)
                    """, ps -> {
                ps.setString(1, callbackFlowNo);
                ps.setString(2, paymentNo);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存支付回调流水失败", ex);
        }
    }

    public void saveRefund(RefundOrder refund) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO refund_order (id, refund_no, order_id, status, amount, reason, created_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), finished_at = VALUES(finished_at)
                    """, ps -> {
                ps.setLong(1, refund.id);
                ps.setString(2, refund.refundNo);
                ps.setLong(3, refund.orderId);
                ps.setString(4, refund.status);
                ps.setBigDecimal(5, refund.amount);
                ps.setString(6, refund.reason);
                setTimestamp(ps, 7, refund.createdAt);
                setTimestamp(ps, 8, refund.finishedAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存退款单失败", ex);
        }
    }

    public void saveDelivery(DeliveryOrder delivery) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO delivery_order
                    (id, order_id, rider_id, rider_name, status, current_step, exception_reason, created_at, delivered_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status = VALUES(status), current_step = VALUES(current_step),
                    rider_id = VALUES(rider_id), rider_name = VALUES(rider_name),
                    exception_reason = VALUES(exception_reason), delivered_at = VALUES(delivered_at)
                    """, ps -> {
                ps.setLong(1, delivery.id);
                ps.setLong(2, delivery.orderId);
                setNullableLong(ps, 3, delivery.riderId);
                ps.setString(4, delivery.riderName);
                ps.setString(5, delivery.status);
                ps.setString(6, delivery.currentStep);
                ps.setString(7, delivery.exceptionReason);
                setTimestamp(ps, 8, delivery.createdAt);
                setTimestamp(ps, 9, delivery.deliveredAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存配送单失败", ex);
        }
    }

    public void saveDeliveryStatusLog(DeliveryStatusLog log) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO delivery_status_log
                    (id, delivery_id, order_id, rider_id, rider_name, before_status, after_status, operator_name, detail, location, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE detail = VALUES(detail)
                    """, ps -> {
                ps.setLong(1, log.id);
                ps.setLong(2, log.deliveryId);
                ps.setLong(3, log.orderId);
                setNullableLong(ps, 4, log.riderId);
                ps.setString(5, log.riderName);
                ps.setString(6, log.beforeStatus);
                ps.setString(7, log.afterStatus);
                ps.setString(8, log.operatorName);
                ps.setString(9, log.detail);
                ps.setString(10, log.location);
                setTimestamp(ps, 11, log.createdAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存配送状态日志失败", ex);
        }
    }

    public void saveReservation(InventoryReservation reservation) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            saveReservation(connection, reservation);
        } catch (Exception ex) {
            throw new IllegalStateException("保存库存占用失败", ex);
        }
    }

    public void saveTicket(Ticket ticket) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO customer_service_ticket (id, order_id, customer_id, status, reason, result, created_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), result = VALUES(result), finished_at = VALUES(finished_at)
                    """, ps -> {
                ps.setLong(1, ticket.id);
                ps.setLong(2, ticket.orderId);
                ps.setLong(3, ticket.customerId);
                ps.setString(4, ticket.status);
                ps.setString(5, ticket.reason);
                ps.setString(6, ticket.result);
                setTimestamp(ps, 7, ticket.createdAt);
                setTimestamp(ps, 8, ticket.finishedAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存售后工单失败", ex);
        }
    }

    public void saveReview(Review review) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            saveReview(connection, review);
        } catch (Exception ex) {
            throw new IllegalStateException("保存评价失败", ex);
        }
    }

    public void saveRisk(RiskRecord record) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO risk_record (id, type, object_type, object_id, reason, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE reason = VALUES(reason), status = VALUES(status)
                    """, ps -> {
                ps.setLong(1, record.id);
                ps.setString(2, record.type);
                ps.setString(3, record.objectType);
                ps.setLong(4, record.objectId);
                ps.setString(5, record.reason);
                ps.setString(6, record.status);
                setTimestamp(ps, 7, record.createdAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存风控记录失败", ex);
        }
    }

    public void saveAudit(AuditLog log) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            saveAudit(connection, log);
        } catch (Exception ex) {
            throw new IllegalStateException("保存审计日志失败", ex);
        }
    }

    public void saveOutbox(OutboxEvent event) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            saveOutbox(connection, event);
        } catch (Exception ex) {
            throw new IllegalStateException("保存 Outbox 事件失败", ex);
        }
    }

    public boolean saveMerchantNotificationIfNew(OutboxEvent event, String consumerName, MerchantNotification notification) {
        if (!enabled()) {
            return true;
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            int inserted = updateCount(connection, """
                    INSERT IGNORE INTO outbox_consume_log
                    (event_id, consumer_name, event_type, aggregate_type, aggregate_id, status, consumed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, ps -> {
                ps.setLong(1, event.id);
                ps.setString(2, consumerName);
                ps.setString(3, event.eventType);
                ps.setString(4, event.aggregateType);
                ps.setLong(5, event.aggregateId);
                ps.setString(6, "CONSUMED");
                setTimestamp(ps, 7, notification.createdAt);
            });
            if (inserted == 0) {
                connection.rollback();
                return false;
            }
            saveMerchantNotification(connection, notification);
            connection.commit();
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("保存 Outbox 消费幂等记录失败", ex);
        }
    }

    public void saveUserCoupon(UserCoupon coupon) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO user_coupon
                    (id, coupon_code, user_id, scene_code, batch_code, template_code, title, discount_amount,
                     threshold_amount, scope, status, locked_order_id, used_order_id, reason,
                     claimed_at, valid_from, valid_to, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), locked_order_id = VALUES(locked_order_id),
                    used_order_id = VALUES(used_order_id), reason = VALUES(reason), updated_at = VALUES(updated_at)
                    """, ps -> {
                ps.setLong(1, coupon.id);
                ps.setString(2, coupon.couponCode);
                ps.setLong(3, coupon.userId);
                ps.setString(4, coupon.sceneCode);
                ps.setString(5, coupon.batchCode);
                ps.setString(6, coupon.templateCode);
                ps.setString(7, coupon.title);
                ps.setBigDecimal(8, coupon.discountAmount);
                ps.setBigDecimal(9, coupon.thresholdAmount);
                ps.setString(10, coupon.scope);
                ps.setString(11, coupon.status);
                setNullableLong(ps, 12, coupon.lockedOrderId);
                setNullableLong(ps, 13, coupon.usedOrderId);
                ps.setString(14, coupon.reason);
                setTimestamp(ps, 15, coupon.claimedAt);
                setTimestamp(ps, 16, coupon.validFrom);
                setTimestamp(ps, 17, coupon.validTo);
                setTimestamp(ps, 18, coupon.updatedAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存用户优惠券失败", ex);
        }
    }

    public void saveCouponStatusLog(CouponStatusLog log) {
        if (!enabled()) {
            return;
        }
        try (Connection connection = connection()) {
            update(connection, """
                    INSERT INTO coupon_status_log
                    (id, coupon_id, coupon_code, user_id, before_status, after_status, related_order_id,
                     operator_name, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE reason = VALUES(reason)
                    """, ps -> {
                ps.setLong(1, log.id);
                ps.setLong(2, log.couponId);
                ps.setString(3, log.couponCode);
                ps.setLong(4, log.userId);
                ps.setString(5, log.beforeStatus);
                ps.setString(6, log.afterStatus);
                setNullableLong(ps, 7, log.relatedOrderId);
                ps.setString(8, log.operatorName);
                ps.setString(9, log.reason);
                setTimestamp(ps, 10, log.createdAt);
            });
        } catch (Exception ex) {
            throw new IllegalStateException("保存优惠券状态日志失败", ex);
        }
    }

    private Connection connection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return DriverManager.getConnection(url, username, password);
    }

    private void executeScript(Connection connection, String resource) throws Exception {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("找不到 SQL 资源：" + resource);
            }
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder statement = new StringBuilder();
            for (String line : sql.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isBlank()) {
                    continue;
                }
                statement.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String command = statement.toString();
                    statement.setLength(0);
                    try (Statement jdbcStatement = connection.createStatement()) {
                        jdbcStatement.execute(command.substring(0, command.lastIndexOf(';')));
                    }
                }
            }
        }
    }

    private void migrateSchema(Connection connection) throws SQLException {
        ensureColumn(connection, "store", "business_hours", "VARCHAR(64) NOT NULL DEFAULT '09:00-22:00'");
        ensureColumn(connection, "store", "delivery_range_km", "DECIMAL(8,2) NOT NULL DEFAULT 6.00");
        ensureColumn(connection, "order_main", "coupon_code", "VARCHAR(64) NULL");
        ensureColumn(connection, "review", "merchant_reply", "VARCHAR(500) NULL");
        ensureColumn(connection, "review", "merchant_replied_at", "TIMESTAMP NULL");
        relaxDeliveryRiderColumns(connection);
    }

    private void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rs = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void relaxDeliveryRiderColumns(Connection connection) throws SQLException {
        if (columnNullable(connection, "delivery_order", "rider_id")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (foreignKeyExists(connection, "delivery_order", "fk_delivery_rider")) {
                statement.execute("ALTER TABLE delivery_order DROP FOREIGN KEY fk_delivery_rider");
            }
            statement.execute("ALTER TABLE delivery_order MODIFY rider_id BIGINT NULL");
            statement.execute("ALTER TABLE delivery_order MODIFY rider_name VARCHAR(128) NULL");
            statement.execute("""
                    ALTER TABLE delivery_order
                    ADD CONSTRAINT fk_delivery_rider FOREIGN KEY (rider_id) REFERENCES user_account(id)
                    """);
        }
    }

    private boolean columnNullable(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rs = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return DatabaseMetaData.columnNullable == rs.getInt("NULLABLE");
            }
        }
        return false;
    }

    private boolean foreignKeyExists(Connection connection, String table, String fkName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rs = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
            while (rs.next()) {
                if (fkName.equalsIgnoreCase(rs.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void upsertStore(Connection connection, Store store) throws SQLException {
        update(connection, """
                INSERT INTO store
                (id, merchant_id, name, notice, open_flag, min_delivery_amount, delivery_fee, rating, category, area,
                 logo_text, avg_delivery_minutes, distance_km, monthly_sales, status_message, delivery_guarantee, delivery_priority,
                 business_hours, delivery_range_km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                merchant_id = VALUES(merchant_id), name = VALUES(name), notice = VALUES(notice), open_flag = VALUES(open_flag),
                min_delivery_amount = VALUES(min_delivery_amount), delivery_fee = VALUES(delivery_fee), rating = VALUES(rating),
                category = VALUES(category), area = VALUES(area), logo_text = VALUES(logo_text),
                avg_delivery_minutes = VALUES(avg_delivery_minutes), distance_km = VALUES(distance_km),
                monthly_sales = VALUES(monthly_sales), status_message = VALUES(status_message),
                delivery_guarantee = VALUES(delivery_guarantee), delivery_priority = VALUES(delivery_priority),
                business_hours = VALUES(business_hours), delivery_range_km = VALUES(delivery_range_km)
                """, ps -> {
            ps.setLong(1, store.id);
            ps.setLong(2, store.merchantId);
            ps.setString(3, store.name);
            ps.setString(4, store.notice);
            ps.setBoolean(5, store.open);
            ps.setBigDecimal(6, store.minDeliveryAmount);
            ps.setBigDecimal(7, store.deliveryFee);
            ps.setDouble(8, store.rating);
            ps.setString(9, store.category);
            ps.setString(10, store.area);
            ps.setString(11, store.logoText);
            ps.setInt(12, store.avgDeliveryMinutes);
            ps.setDouble(13, store.distanceKm);
            ps.setInt(14, store.monthlySales);
            ps.setString(15, store.statusMessage);
            ps.setString(16, store.deliveryGuarantee);
            ps.setInt(17, store.deliveryPriority);
            ps.setString(18, store.businessHours);
            ps.setDouble(19, store.deliveryRangeKm);
        });
    }

    private void insertStoreChildren(Connection connection, Store store) throws SQLException {
        int sortNo = 0;
        for (String tag : store.tags) {
            update(connection, "INSERT IGNORE INTO store_tag (store_id, tag) VALUES (?, ?)", ps -> {
                ps.setLong(1, store.id);
                ps.setString(2, tag);
            });
        }
        for (String promotion : store.promotions) {
            int currentSort = sortNo++;
            update(connection, "INSERT IGNORE INTO store_promotion (store_id, promotion, sort_no) VALUES (?, ?, ?)", ps -> {
                ps.setLong(1, store.id);
                ps.setString(2, promotion);
                ps.setInt(3, currentSort);
            });
        }
        sortNo = 0;
        for (String couponHint : store.couponHints) {
            int currentSort = sortNo++;
            update(connection, "INSERT IGNORE INTO store_coupon_hint (store_id, coupon_hint, sort_no) VALUES (?, ?, ?)", ps -> {
                ps.setLong(1, store.id);
                ps.setString(2, couponHint);
                ps.setInt(3, currentSort);
            });
        }
    }

    private void replaceStoreChildren(Connection connection, Store store) throws SQLException {
        update(connection, "DELETE FROM store_tag WHERE store_id = ?", ps -> ps.setLong(1, store.id));
        update(connection, "DELETE FROM store_promotion WHERE store_id = ?", ps -> ps.setLong(1, store.id));
        update(connection, "DELETE FROM store_coupon_hint WHERE store_id = ?", ps -> ps.setLong(1, store.id));
        insertStoreChildren(connection, store);
    }

    private void insertProductIgnore(Connection connection, Product product) throws SQLException {
        update(connection, """
                INSERT IGNORE INTO product
                (id, store_id, name, description, price, stock, on_sale, monthly_sales, category, image_tone, ranking, discount_label, original_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ps -> bindProduct(ps, product));
    }

    private boolean reserveStockBatch(Connection connection, List<OrderItem> items) throws SQLException {
        for (OrderItem item : items) {
            int updated = updateCount(connection, """
                    UPDATE product
                    SET stock = stock - ?
                    WHERE id = ? AND on_sale = TRUE AND stock >= ?
                    """, ps -> {
                ps.setInt(1, item.quantity);
                ps.setLong(2, item.productId);
                ps.setInt(3, item.quantity);
            });
            if (updated != 1) {
                return false;
            }
        }
        return true;
    }

    private void saveOrderAggregate(Connection connection, Order order) throws SQLException {
        update(connection, """
                INSERT INTO order_main
                (id, order_no, customer_id, customer_name, store_id, store_name, status, item_amount, delivery_fee,
                 discount_amount, pay_amount, idempotency_token, coupon_code, payment_order_no, delivery_id, refund_id, receiver,
                 phone_masked, detail_masked, distance_km, estimated_delivery_minutes, created_at, paid_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                status = VALUES(status), item_amount = VALUES(item_amount), delivery_fee = VALUES(delivery_fee),
                discount_amount = VALUES(discount_amount), pay_amount = VALUES(pay_amount),
                coupon_code = VALUES(coupon_code), payment_order_no = VALUES(payment_order_no),
                delivery_id = VALUES(delivery_id), refund_id = VALUES(refund_id),
                paid_at = VALUES(paid_at), finished_at = VALUES(finished_at)
                """, ps -> {
            ps.setLong(1, order.id);
            ps.setString(2, order.orderNo);
            ps.setLong(3, order.customerId);
            ps.setString(4, order.customerName);
            ps.setLong(5, order.storeId);
            ps.setString(6, order.storeName);
            ps.setString(7, order.status.name());
            ps.setBigDecimal(8, order.itemAmount);
            ps.setBigDecimal(9, order.deliveryFee);
            ps.setBigDecimal(10, order.discountAmount);
            ps.setBigDecimal(11, order.payAmount);
            ps.setString(12, order.idempotencyToken);
            ps.setString(13, order.couponCode);
            ps.setString(14, order.paymentOrderNo);
            setNullableLong(ps, 15, order.deliveryId);
            setNullableLong(ps, 16, order.refundId);
            ps.setString(17, order.addressSnapshot.receiver);
            ps.setString(18, order.addressSnapshot.phoneMasked);
            ps.setString(19, order.addressSnapshot.detailMasked);
            ps.setDouble(20, order.addressSnapshot.distanceKm);
            ps.setInt(21, order.estimatedDeliveryMinutes);
            setTimestamp(ps, 22, order.createdAt);
            setTimestamp(ps, 23, order.paidAt);
            setTimestamp(ps, 24, order.finishedAt);
        });
        for (OrderItem item : order.items) {
            update(connection, """
                    INSERT INTO order_item (order_id, product_id, product_name, price, quantity, subtotal)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE product_name = VALUES(product_name), price = VALUES(price),
                    quantity = VALUES(quantity), subtotal = VALUES(subtotal)
                    """, ps -> {
                ps.setLong(1, order.id);
                ps.setLong(2, item.productId);
                ps.setString(3, item.productName);
                ps.setBigDecimal(4, item.price);
                ps.setInt(5, item.quantity);
                ps.setBigDecimal(6, item.subtotal);
            });
        }
        for (OrderStatusRecord record : order.statusRecords) {
            saveStatusRecord(connection, record);
        }
        for (FulfillmentStep step : order.fulfillmentSteps) {
            saveFulfillmentStep(connection, step);
        }
        if (order.review != null) {
            saveReview(connection, order.review);
        }
    }

    private void savePayment(Connection connection, PaymentOrder payment) throws SQLException {
        update(connection, """
                INSERT INTO payment_order (id, payment_no, order_id, status, amount, callback_flow_no, paid_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status = VALUES(status), callback_flow_no = VALUES(callback_flow_no), paid_at = VALUES(paid_at)
                """, ps -> {
            ps.setLong(1, payment.id);
            ps.setString(2, payment.paymentNo);
            ps.setLong(3, payment.orderId);
            ps.setString(4, payment.status);
            ps.setBigDecimal(5, payment.amount);
            ps.setString(6, payment.callbackFlowNo);
            setTimestamp(ps, 7, payment.paidAt);
        });
    }

    private void saveReservation(Connection connection, InventoryReservation reservation) throws SQLException {
        update(connection, """
                INSERT INTO inventory_reservation (id, order_id, product_id, product_name, quantity, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status = VALUES(status)
                """, ps -> {
            ps.setLong(1, reservation.id);
            ps.setLong(2, reservation.orderId);
            ps.setLong(3, reservation.productId);
            ps.setString(4, reservation.productName);
            ps.setInt(5, reservation.quantity);
            ps.setString(6, reservation.status);
            setTimestamp(ps, 7, reservation.createdAt);
        });
    }

    private void saveAudit(Connection connection, AuditLog log) throws SQLException {
        update(connection, """
                INSERT INTO audit_log
                (id, actor_id, actor_name, actor_role, action, object_type, object_id, before_status, after_status, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reason = VALUES(reason)
                """, ps -> {
            ps.setLong(1, log.id);
            ps.setLong(2, log.actorId);
            ps.setString(3, log.actorName);
            ps.setString(4, log.actorRole.name());
            ps.setString(5, log.action);
            ps.setString(6, log.objectType);
            ps.setLong(7, log.objectId);
            ps.setString(8, log.beforeStatus);
            ps.setString(9, log.afterStatus);
            ps.setString(10, log.reason);
            setTimestamp(ps, 11, log.createdAt);
        });
    }

    private void saveOutbox(Connection connection, OutboxEvent event) throws SQLException {
        update(connection, """
                INSERT INTO outbox_event (id, event_type, aggregate_type, aggregate_id, payload, status, retry_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status = VALUES(status), retry_count = VALUES(retry_count)
                """, ps -> {
            ps.setLong(1, event.id);
            ps.setString(2, event.eventType);
            ps.setString(3, event.aggregateType);
            ps.setLong(4, event.aggregateId);
            ps.setString(5, event.payload);
            ps.setString(6, event.status);
            ps.setInt(7, event.retryCount);
            setTimestamp(ps, 8, event.createdAt);
        });
    }

    private void saveMerchantNotification(Connection connection, MerchantNotification notification) throws SQLException {
        update(connection, """
                INSERT INTO merchant_notification (id, event_id, order_id, store_id, title, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE message = VALUES(message)
                """, ps -> {
            ps.setLong(1, notification.id);
            ps.setLong(2, notification.eventId);
            ps.setLong(3, notification.orderId);
            ps.setLong(4, notification.storeId);
            ps.setString(5, notification.title);
            ps.setString(6, notification.message);
            setTimestamp(ps, 7, notification.createdAt);
        });
    }

    private void bindProduct(PreparedStatement ps, Product product) throws SQLException {
        ps.setLong(1, product.id);
        ps.setLong(2, product.storeId);
        ps.setString(3, product.name);
        ps.setString(4, product.description);
        ps.setBigDecimal(5, product.price);
        ps.setInt(6, product.stock);
        ps.setBoolean(7, product.onSale);
        ps.setInt(8, product.monthlySales);
        ps.setString(9, product.category);
        ps.setString(10, product.imageTone);
        ps.setInt(11, product.ranking);
        ps.setString(12, product.discountLabel);
        ps.setBigDecimal(13, product.originalPrice == null ? product.price : product.originalPrice);
    }

    private List<String> loadStringList(Connection connection, String sql, long id) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        }
        return values;
    }

    private List<OrderItem> loadOrderItems(Connection connection, long orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_item WHERE order_id = ? ORDER BY product_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new OrderItem(
                            rs.getLong("product_id"),
                            rs.getString("product_name"),
                            rs.getBigDecimal("price"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("subtotal")));
                }
            }
        }
        return items;
    }

    private List<OrderStatusRecord> loadStatusRecords(Connection connection, long orderId) throws SQLException {
        List<OrderStatusRecord> records = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_status_record WHERE order_id = ? ORDER BY created_at, id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new OrderStatusRecord(
                            rs.getLong("id"),
                            orderId,
                            nullableOrderStatus(rs, "before_status"),
                            OrderStatus.valueOf(rs.getString("after_status")),
                            rs.getString("operator_name"),
                            rs.getString("reason"),
                            ldt(rs, "created_at")));
                }
            }
        }
        return records;
    }

    private List<FulfillmentStep> loadFulfillmentSteps(Connection connection, long orderId) throws SQLException {
        List<FulfillmentStep> steps = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM fulfillment_step WHERE order_id = ? ORDER BY created_at, id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    steps.add(new FulfillmentStep(
                            rs.getLong("id"),
                            orderId,
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("operator_name"),
                            rs.getString("detail"),
                            ldt(rs, "created_at")));
                }
            }
        }
        return steps;
    }

    private Review loadReview(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM review WHERE order_id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Review(
                            rs.getLong("id"),
                            orderId,
                            rs.getLong("customer_id"),
                            rs.getInt("score"),
                            rs.getString("content"),
                            rs.getString("merchant_reply"),
                            ldt(rs, "merchant_replied_at"),
                            ldt(rs, "created_at"));
                }
            }
        }
        return null;
    }

    private void saveStatusRecord(Connection connection, OrderStatusRecord record) throws SQLException {
        update(connection, """
                INSERT INTO order_status_record (id, order_id, before_status, after_status, operator_name, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reason = VALUES(reason)
                """, ps -> {
            ps.setLong(1, record.id);
            ps.setLong(2, record.orderId);
            ps.setString(3, record.beforeStatus == null ? null : record.beforeStatus.name());
            ps.setString(4, record.afterStatus.name());
            ps.setString(5, record.operator);
            ps.setString(6, record.reason);
            setTimestamp(ps, 7, record.createdAt);
        });
    }

    private void saveFulfillmentStep(Connection connection, FulfillmentStep step) throws SQLException {
        update(connection, """
                INSERT INTO fulfillment_step (id, order_id, title, status, operator_name, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE detail = VALUES(detail)
                """, ps -> {
            ps.setLong(1, step.id);
            ps.setLong(2, step.orderId);
            ps.setString(3, step.title);
            ps.setString(4, step.status);
            ps.setString(5, step.operator);
            ps.setString(6, step.detail);
            setTimestamp(ps, 7, step.createdAt);
        });
    }

    private void saveReview(Connection connection, Review review) throws SQLException {
        update(connection, """
                INSERT INTO review (id, order_id, customer_id, score, content, merchant_reply, merchant_replied_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score), content = VALUES(content),
                merchant_reply = VALUES(merchant_reply), merchant_replied_at = VALUES(merchant_replied_at)
                """, ps -> {
            ps.setLong(1, review.id);
            ps.setLong(2, review.orderId);
            ps.setLong(3, review.customerId);
            ps.setInt(4, review.score);
            ps.setString(5, review.content);
            ps.setString(6, review.merchantReply);
            setTimestamp(ps, 7, review.repliedAt);
            setTimestamp(ps, 8, review.createdAt);
        });
    }

    private void query(Connection connection, String sql, RowConsumer consumer) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                consumer.accept(rs);
            }
        }
    }

    private void update(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private int updateCount(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private OrderStatus nullableOrderStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : OrderStatus.valueOf(value);
    }

    private void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setTimestamp(index, null);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private LocalDateTime ldt(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    public static class CatalogState {
        public final List<Account> accounts = new ArrayList<>();
        public final List<Address> addresses = new ArrayList<>();
        public final List<Channel> channels = new ArrayList<>();
        public final List<Banner> banners = new ArrayList<>();
        public final List<Store> stores = new ArrayList<>();
        public final List<Product> products = new ArrayList<>();
    }

    public static class TransactionState {
        public final Map<Long, Order> orders = new LinkedHashMap<>();
        public final Map<Long, PaymentOrder> payments = new LinkedHashMap<>();
        public final Map<Long, RefundOrder> refunds = new LinkedHashMap<>();
        public final Map<Long, DeliveryOrder> deliveries = new LinkedHashMap<>();
        public final Map<Long, DeliveryStatusLog> deliveryStatusLogs = new LinkedHashMap<>();
        public final Map<Long, List<CartItem>> carts = new LinkedHashMap<>();
        public final Map<Long, Ticket> tickets = new LinkedHashMap<>();
        public final Map<Long, Review> reviews = new LinkedHashMap<>();
        public final Map<Long, InventoryReservation> reservations = new LinkedHashMap<>();
        public final Map<Long, RiskRecord> riskRecords = new LinkedHashMap<>();
        public final Map<Long, AuditLog> auditLogs = new LinkedHashMap<>();
        public final Map<Long, OutboxEvent> outboxEvents = new LinkedHashMap<>();
        public final Map<String, OutboxConsumeRecord> outboxConsumeRecords = new LinkedHashMap<>();
        public final Map<Long, MerchantNotification> merchantNotifications = new LinkedHashMap<>();
        public final Map<Long, OnboardingApplication> onboardingApplications = new LinkedHashMap<>();
        public final Map<Long, UserCoupon> userCoupons = new LinkedHashMap<>();
        public final Map<Long, CouponStatusLog> couponStatusLogs = new LinkedHashMap<>();
        public final Map<String, Long> idempotentOrders = new LinkedHashMap<>();
        public final java.util.Set<String> paymentCallbackFlows = new java.util.LinkedHashSet<>();
    }
}
