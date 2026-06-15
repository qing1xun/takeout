package com.cuifeng.backend.takeout;

import com.cuifeng.backend.auth.Role;
import com.cuifeng.backend.common.BusinessException;
import com.cuifeng.backend.infrastructure.RabbitOutboxPublisher;
import com.cuifeng.backend.infrastructure.RedisFeatureStore;
import com.cuifeng.backend.order.OrderStateMachine;
import com.cuifeng.backend.order.OrderStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

@Service
public class TakeoutService {
    private static final BigDecimal ZERO = money("0");
    private static final BigDecimal DELIVERY_FEE = money("4");
    private static final int UNPAID_ORDER_TIMEOUT_MINUTES = 15;
    private static final int TOKEN_TTL_HOURS = 8;
    private static final String MERCHANT_ORDER_CREATED_CONSUMER = "merchant-order-created-notifier";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String DEMO_PASSWORD_HASH = PASSWORD_ENCODER.encode("123456");

    private final AtomicLong ids = new AtomicLong(10000);
    private final Map<Long, Account> accounts = new LinkedHashMap<>();
    private final Map<String, AuthSession> tokens = new ConcurrentHashMap<>();
    private final Map<Long, Address> addresses = new LinkedHashMap<>();
    private final Map<Long, Store> stores = new LinkedHashMap<>();
    private final Map<Long, Product> products = new LinkedHashMap<>();
    private final List<Channel> channels = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();
    private final Map<Long, List<CartItem>> carts = new LinkedHashMap<>();
    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final Map<Long, PaymentOrder> payments = new LinkedHashMap<>();
    private final Map<Long, RefundOrder> refunds = new LinkedHashMap<>();
    private final Map<Long, DeliveryOrder> deliveries = new LinkedHashMap<>();
    private final Map<Long, DeliveryStatusLog> deliveryStatusLogs = new LinkedHashMap<>();
    private final Map<Long, Ticket> tickets = new LinkedHashMap<>();
    private final Map<Long, Review> reviews = new LinkedHashMap<>();
    private final Map<Long, OnboardingApplication> onboardingApplications = new LinkedHashMap<>();
    private final Map<Long, InventoryReservation> reservations = new LinkedHashMap<>();
    private final Map<Long, RiskRecord> riskRecords = new LinkedHashMap<>();
    private final Map<Long, AuditLog> auditLogs = new LinkedHashMap<>();
    private final Map<Long, OutboxEvent> outboxEvents = new LinkedHashMap<>();
    private final Map<String, OutboxConsumeRecord> outboxConsumeRecords = new LinkedHashMap<>();
    private final Map<Long, MerchantNotification> merchantNotifications = new LinkedHashMap<>();
    private final Map<String, Long> idempotentOrders = new ConcurrentHashMap<>();
    private final Set<String> paymentCallbackFlows = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> couponOrderLocks = new ConcurrentHashMap<>();
    private final Map<String, CouponActivity> couponActivities = new LinkedHashMap<>();
    private final Map<Long, UserCoupon> userCouponInstances = new LinkedHashMap<>();
    private final Map<Long, CouponStatusLog> couponStatusLogs = new LinkedHashMap<>();
    private final Map<String, List<Long>> memoryRateWindows = new ConcurrentHashMap<>();
    private final Map<String, Integer> memoryCouponStocks = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> memoryCouponClaims = new ConcurrentHashMap<>();
    private final Cache<String, StorePage> storePageCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
    private final DatabaseStore databaseStore;
    private final RedisFeatureStore redisFeatureStore;
    private final RabbitOutboxPublisher outboxPublisher;

    public TakeoutService() {
        this(null, null, null);
    }

    @Autowired
    public TakeoutService(DatabaseStore databaseStore,
                          RedisFeatureStore redisFeatureStore,
                          RabbitOutboxPublisher outboxPublisher) {
        this.databaseStore = databaseStore;
        this.redisFeatureStore = redisFeatureStore;
        this.outboxPublisher = outboxPublisher;
        seed();
        initializeDatabaseMode();
        initializeHighConcurrencyState();
    }

    private void initializeDatabaseMode() {
        if (!databaseEnabled()) {
            return;
        }
        databaseStore.initializeSchema();
        databaseStore.bootstrapCatalog(accounts.values(), addresses.values(), channels, banners, stores.values(), products.values());

        DatabaseStore.CatalogState catalog = databaseStore.loadCatalog();
        accounts.clear();
        addresses.clear();
        stores.clear();
        products.clear();
        channels.clear();
        banners.clear();
        catalog.accounts.forEach(account -> accounts.put(account.id, account));
        catalog.addresses.forEach(address -> addresses.put(address.id, address));
        catalog.stores.forEach(store -> stores.put(store.id, store));
        catalog.products.forEach(product -> products.put(product.id, product));
        channels.addAll(catalog.channels);
        banners.addAll(catalog.banners);

        DatabaseStore.TransactionState transactions = databaseStore.loadTransactions();
        orders.clear();
        payments.clear();
        refunds.clear();
        deliveries.clear();
        deliveryStatusLogs.clear();
        tickets.clear();
        reviews.clear();
        reservations.clear();
        riskRecords.clear();
        auditLogs.clear();
        outboxEvents.clear();
        outboxConsumeRecords.clear();
        merchantNotifications.clear();
        onboardingApplications.clear();
        idempotentOrders.clear();
        paymentCallbackFlows.clear();
        couponOrderLocks.clear();
        userCouponInstances.clear();
        couponStatusLogs.clear();
        orders.putAll(transactions.orders);
        payments.putAll(transactions.payments);
        refunds.putAll(transactions.refunds);
        deliveries.putAll(transactions.deliveries);
        deliveryStatusLogs.putAll(transactions.deliveryStatusLogs);
        carts.clear();
        carts.putAll(transactions.carts);
        tickets.putAll(transactions.tickets);
        reviews.putAll(transactions.reviews);
        reservations.putAll(transactions.reservations);
        riskRecords.putAll(transactions.riskRecords);
        auditLogs.putAll(transactions.auditLogs);
        outboxEvents.putAll(transactions.outboxEvents);
        outboxConsumeRecords.putAll(transactions.outboxConsumeRecords);
        merchantNotifications.putAll(transactions.merchantNotifications);
        onboardingApplications.putAll(transactions.onboardingApplications);
        userCouponInstances.putAll(transactions.userCoupons);
        couponStatusLogs.putAll(transactions.couponStatusLogs);
        userCouponInstances.values().stream()
                .filter(coupon -> "LOCKED".equals(coupon.status) && coupon.lockedOrderId != null)
                .forEach(coupon -> couponOrderLocks.put(couponLockKey(coupon.userId, coupon.couponCode), coupon.lockedOrderId));
        idempotentOrders.putAll(transactions.idempotentOrders);
        paymentCallbackFlows.addAll(transactions.paymentCallbackFlows);
        ids.set(Math.max(ids.get(), databaseStore.maxKnownId()));
    }

    private boolean databaseEnabled() {
        return databaseStore != null && databaseStore.enabled();
    }

    private boolean redisEnabled() {
        return redisFeatureStore != null && redisFeatureStore.enabled();
    }

    private void initializeHighConcurrencyState() {
        seedCouponActivities();
        restoreCouponClaimCounters();
        if (databaseEnabled()) {
            databaseStore.bootstrapCouponActivities(couponActivities.values());
        }
        if (redisEnabled()) {
            redisFeatureStore.initializeCatalog(products.values());
            couponActivities.values().forEach(activity ->
                    redisFeatureStore.initializeCouponStock(activity.batchCode, activity.stock));
        }
    }

    private void seedCouponActivities() {
        LocalDate today = LocalDate.now();
        couponActivities.clear();
        addCouponActivity(new CouponActivity(
                "MEMBER",
                "MEMBER-" + today,
                "会员红包日券包",
                "今日可领 6 张券，按券实例独立锁定、核销和释放",
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                1,
                10000,
                List.of(
                        new CouponTemplate("MEMBER_5_30", "会员 5 元红包", money("5"), money("30"), "全品类"),
                        new CouponTemplate("MEMBER_3_20", "会员 3 元红包", money("3"), money("20"), "美食/饮品"),
                        new CouponTemplate("MEMBER_2_DRINK", "饮品 2 元券", money("2"), money("15"), "甜点饮品"),
                        new CouponTemplate("MEMBER_4_NIGHT", "夜宵 4 元券", money("4"), money("35"), "夜宵"),
                        new CouponTemplate("MEMBER_3_DELIVERY", "配送费 3 元券", money("3"), money("20"), "配送费抵扣"),
                        new CouponTemplate("MEMBER_10_80", "满 80 减 10 红包", money("10"), money("80"), "大额订单"))));
        addCouponActivity(new CouponActivity(
                "FAST",
                "FAST-" + today,
                "30 分钟必达专区券",
                "高峰期优先调度专区券，每日一张",
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                1,
                3000,
                List.of(new CouponTemplate("FAST_3_20", "30 分钟必达 3 元券", money("3"), money("20"), "准时达专区"))));
        addCouponActivity(new CouponActivity(
                "B2B",
                "B2B-" + today,
                "企业午餐券",
                "工作日企业订餐优惠券，每日一张",
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                1,
                2000,
                List.of(new CouponTemplate("B2B_10_80", "企业午餐 10 元券", money("10"), money("80"), "企业午餐"))));
    }

    private void addCouponActivity(CouponActivity activity) {
        couponActivities.put(activity.sceneCode, activity);
        memoryCouponStocks.putIfAbsent(activity.batchCode, activity.stock);
    }

    private void restoreCouponClaimCounters() {
        for (CouponActivity activity : couponActivities.values()) {
            Set<Long> claimedUsers = ConcurrentHashMap.newKeySet();
            userCouponInstances.values().stream()
                    .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                    .forEach(coupon -> claimedUsers.add(coupon.userId));
            memoryCouponClaims.put(activity.batchCode, claimedUsers);
            memoryCouponStocks.put(activity.batchCode, Math.max(0, activity.stock - claimedUsers.size()));
        }
    }

    private void persistProduct(Product product) {
        if (databaseEnabled() && product != null) {
            databaseStore.saveProduct(product);
        }
    }

    private void persistAccount(Account account) {
        if (databaseEnabled() && account != null) {
            databaseStore.saveAccount(account);
        }
    }

    private void persistOnboardingApplication(OnboardingApplication application) {
        if (databaseEnabled() && application != null) {
            databaseStore.saveOnboardingApplication(application);
        }
    }

    private void persistAddress(Address address) {
        if (databaseEnabled() && address != null) {
            databaseStore.saveAddress(address);
        }
    }

    private void deleteAddressRecord(long addressId) {
        if (databaseEnabled()) {
            databaseStore.deleteAddress(addressId);
        }
    }

    private void persistStore(Store store) {
        if (databaseEnabled() && store != null) {
            databaseStore.saveStore(store);
        }
        storePageCache.invalidateAll();
    }

    private void persistCartItem(long userId, CartItem item) {
        if (databaseEnabled() && item != null) {
            databaseStore.saveCartItem(userId, item);
        }
    }

    private void deleteCartItemRecord(long userId, long productId) {
        if (databaseEnabled()) {
            databaseStore.deleteCartItem(userId, productId);
        }
    }

    private void deleteCartItems(long userId) {
        if (databaseEnabled()) {
            databaseStore.deleteCartItems(userId);
        }
    }

    private void persistOrderAggregate(Order order) {
        if (databaseEnabled() && order != null) {
            databaseStore.saveOrderAggregate(order);
        }
    }

    private void persistPayment(PaymentOrder payment) {
        if (databaseEnabled() && payment != null) {
            databaseStore.savePayment(payment);
        }
    }

    private void persistPaymentCallback(String callbackFlowNo, String paymentNo) {
        if (databaseEnabled() && callbackFlowNo != null) {
            databaseStore.savePaymentCallback(callbackFlowNo, paymentNo);
        }
    }

    private void persistRefund(RefundOrder refund) {
        if (databaseEnabled() && refund != null) {
            databaseStore.saveRefund(refund);
        }
    }

    private void persistDelivery(DeliveryOrder delivery) {
        if (databaseEnabled() && delivery != null) {
            databaseStore.saveDelivery(delivery);
        }
    }

    private void persistDeliveryStatusLog(DeliveryStatusLog log) {
        if (databaseEnabled() && log != null) {
            databaseStore.saveDeliveryStatusLog(log);
        }
    }

    private void persistReservation(InventoryReservation reservation) {
        if (databaseEnabled() && reservation != null) {
            databaseStore.saveReservation(reservation);
        }
    }

    private void persistTicket(Ticket ticket) {
        if (databaseEnabled() && ticket != null) {
            databaseStore.saveTicket(ticket);
        }
    }

    private void persistReview(Review review) {
        if (databaseEnabled() && review != null) {
            databaseStore.saveReview(review);
        }
    }

    private void persistRisk(RiskRecord record) {
        if (databaseEnabled() && record != null) {
            databaseStore.saveRisk(record);
        }
    }

    private void persistAudit(AuditLog log) {
        if (databaseEnabled() && log != null) {
            databaseStore.saveAudit(log);
        }
    }

    private void persistOutbox(OutboxEvent event) {
        if (databaseEnabled() && event != null) {
            databaseStore.saveOutbox(event);
        }
    }

    private void persistUserCoupon(UserCoupon coupon) {
        if (databaseEnabled() && coupon != null) {
            databaseStore.saveUserCoupon(coupon);
        }
    }

    private void persistCouponStatusLog(CouponStatusLog log) {
        if (databaseEnabled() && log != null) {
            databaseStore.saveCouponStatusLog(log);
        }
    }

    public synchronized SessionView login(LoginRequest request) {
        LoginRequest value = request == null ? new LoginRequest() : request;
        String username = requireText(value.username, "账号不能为空");
        Role portal = requirePortal(value.portal);
        Account account = accounts.values().stream()
                .filter(item -> item.username.equals(username) && passwordMatches(value.password, item.password))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (account.role != portal) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号不能登录该端");
        }
        AuthSession session = issueToken(account, portal);
        audit(account, "LOGIN", "ACCOUNT", account.id, "-", account.role.name(), portal.name() + " 端登录");
        return sessionView(session, account);
    }

    public synchronized SessionView registerCustomer(CustomerRegisterRequest request) {
        CustomerRegisterRequest value = request == null ? new CustomerRegisterRequest() : request;
        String username = requireUsername(value.username);
        String password = requirePassword(value.password);
        String displayName = requireText(value.displayName, "昵称不能为空");
        String phone = requirePhone(value.phone);
        ensureUsernameAvailable(username);
        ensurePhoneAvailable(phone);
        Account account = new Account(ids.incrementAndGet(), username, passwordHash(password), displayName, Role.CUSTOMER, null, phone);
        accounts.put(account.id, account);
        persistAccount(account);
        AuthSession session = issueToken(account, Role.CUSTOMER);
        audit(account, "REGISTER_CUSTOMER", "ACCOUNT", account.id, "-", "CUSTOMER", "用户自助注册");
        return sessionView(session, account);
    }

    public synchronized OnboardingApplicationView submitMerchantApplication(OnboardingApplicationRequest request) {
        return submitApplication(Role.MERCHANT, request);
    }

    public synchronized OnboardingApplicationView submitRiderApplication(OnboardingApplicationRequest request) {
        return submitApplication(Role.RIDER, request);
    }

    private OnboardingApplicationView submitApplication(Role role, OnboardingApplicationRequest request) {
        OnboardingApplicationRequest value = request == null ? new OnboardingApplicationRequest() : request;
        String applicantName = requireText(value.applicantName, "申请人不能为空");
        String phone = requirePhone(value.phone);
        if (role == Role.MERCHANT) {
            requireText(value.storeName, "门店名称不能为空");
            requireText(value.category, "经营品类不能为空");
            requireText(value.address, "门店地址不能为空");
        }
        String preferredUsername = value.preferredUsername == null || value.preferredUsername.isBlank()
                ? ""
                : requireUsername(value.preferredUsername);
        if (!preferredUsername.isBlank()) {
            ensureUsernameAvailable(preferredUsername);
        }
        ensureNoPendingApplication(role, phone);
        OnboardingApplication application = new OnboardingApplication(
                ids.incrementAndGet(),
                role,
                "PENDING",
                applicantName,
                phone,
                defaultText(value.storeName, ""),
                defaultText(value.category, ""),
                defaultText(value.address, ""),
                preferredUsername,
                defaultText(value.reason, "入驻申请"),
                "等待后台审核",
                null,
                null,
                LocalDateTime.now(),
                null);
        onboardingApplications.put(application.id, application);
        persistOnboardingApplication(application);
        audit(systemActor(), "SUBMIT_ONBOARDING", "APPLICATION", application.id, "-", "PENDING",
                role.name() + " 入驻申请");
        return applicationView(application);
    }

    public synchronized List<OnboardingApplicationView> adminOnboardingApplications(String status) {
        String statusValue = Objects.toString(status, "").trim();
        return onboardingApplications.values().stream()
                .filter(application -> statusValue.isBlank() || application.status.equalsIgnoreCase(statusValue))
                .map(this::applicationView)
                .sorted(Comparator.comparing((OnboardingApplicationView application) -> application.createdAt()).reversed())
                .toList();
    }

    public synchronized OnboardingApplicationView approveOnboardingApplication(Account admin, long applicationId, OnboardingApprovalRequest request) {
        OnboardingApplication application = requireOnboardingApplication(applicationId);
        if (!"PENDING".equals(application.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有待审核申请可以通过");
        }
        OnboardingApprovalRequest value = request == null ? new OnboardingApprovalRequest() : request;
        String rawPassword = value.initialPassword == null || value.initialPassword.isBlank() ? "123456" : requirePassword(value.initialPassword);
        String username = value.username == null || value.username.isBlank()
                ? application.preferredUsername
                : requireUsername(value.username);
        if (username == null || username.isBlank()) {
            username = application.role == Role.MERCHANT
                    ? uniqueUsername("merchant_app_" + application.id)
                    : uniqueUsername("rider_app_" + application.id);
        } else {
            ensureUsernameAvailable(username);
        }
        long accountId = ids.incrementAndGet();
        Long storeId = null;
        if (application.role == Role.MERCHANT) {
            storeId = ids.incrementAndGet();
        }
        Account account = new Account(accountId, username, passwordHash(rawPassword),
                application.role == Role.MERCHANT ? application.storeName + "商家" : application.applicantName + "骑手",
                application.role, storeId, application.phone);
        accounts.put(account.id, account);
        persistAccount(account);
        if (application.role == Role.MERCHANT) {
            Store store = new Store(storeId, account.id, application.storeName,
                    "新入驻门店，商家需完善公告、活动和商品", false,
                    money("20"), DELIVERY_FEE, 4.5, List.of(application.category, "新入驻"),
                    application.category, application.address, application.storeName.substring(0, 1),
                    35, 3.0, 0, "新店待配置履约能力", "准时宝", List.of(), List.of(), 50);
            stores.put(store.id, store);
            persistStore(store);
        }
        application.status = "APPROVED";
        application.result = value.result == null || value.result.isBlank() ? "审核通过，账号已创建" : value.result;
        application.createdAccountId = account.id;
        application.createdStoreId = storeId;
        application.finishedAt = LocalDateTime.now();
        persistOnboardingApplication(application);
        audit(admin, "APPROVE_ONBOARDING", "APPLICATION", application.id, "PENDING", "APPROVED",
                "创建账号 " + account.username);
        return applicationView(application);
    }

    public synchronized OnboardingApplicationView rejectOnboardingApplication(Account admin, long applicationId, OnboardingApprovalRequest request) {
        OnboardingApplication application = requireOnboardingApplication(applicationId);
        if (!"PENDING".equals(application.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有待审核申请可以驳回");
        }
        OnboardingApprovalRequest value = request == null ? new OnboardingApprovalRequest() : request;
        application.status = "REJECTED";
        application.result = value.result == null || value.result.isBlank() ? "审核驳回，请补充材料后重新提交" : value.result;
        application.finishedAt = LocalDateTime.now();
        persistOnboardingApplication(application);
        audit(admin, "REJECT_ONBOARDING", "APPLICATION", application.id, "PENDING", "REJECTED", application.result);
        return applicationView(application);
    }

    public synchronized void logout(String authorization) {
        String token = parseToken(authorization);
        if (token != null) {
            tokens.remove(token);
        }
    }

    public synchronized void logoutAll(String authorization) {
        AuthSession session = activeSession(parseToken(authorization));
        tokens.entrySet().removeIf(entry -> entry.getValue().accountId == session.accountId);
    }

    public SessionView me(String authorization) {
        AuthSession session = activeSession(parseToken(authorization));
        Account account = accounts.get(session.accountId);
        if (account == null) {
            tokens.remove(session.token);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return sessionView(session, account);
    }

    public Account require(String authorization, Role... roles) {
        AuthSession session = activeSession(parseToken(authorization));
        Account account = accounts.get(session.accountId);
        if (account == null) {
            tokens.remove(session.token);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (roles.length > 0) {
            boolean allowed = false;
            for (Role role : roles) {
                if (account.role == role) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "当前角色无权访问该接口");
            }
        }
        return account;
    }

    public synchronized List<Address> userAddresses(Account account) {
        return addresses.values().stream()
                .filter(address -> address.userId == account.id)
                .sorted(Comparator.comparing(address -> !address.defaultAddress))
                .toList();
    }

    public synchronized UserProfileView userProfile(Account account) {
        long couponCount = userCoupons(account).stream()
                .filter(coupon -> "UNUSED".equals(coupon.status()) || "LOCKED".equals(coupon.status()))
                .count();
        long pendingTicketCount = tickets.values().stream()
                .filter(ticket -> ticket.customerId == account.id)
                .filter(ticket -> "PENDING".equals(ticket.status))
                .count();
        return new UserProfileView(
                account.id,
                account.username,
                account.displayName,
                maskPhone(account.phone),
                userAddresses(account).size(),
                couponCount,
                userOrders(account).size(),
                userReviews(account).size(),
                pendingTicketCount);
    }

    public synchronized List<CouponWalletView> userCoupons(Account account) {
        return userCouponInstances.values().stream()
                .filter(coupon -> coupon.userId == account.id)
                .peek(this::refreshCouponStatus)
                .map(this::couponWalletView)
                .sorted(Comparator.comparing(CouponWalletView::status)
                        .thenComparing(CouponWalletView::validTo)
                        .thenComparing(CouponWalletView::couponCode))
                .toList();
    }

    public synchronized Address createAddress(Account account, AddressRequest request) {
        AddressRequest value = request == null ? new AddressRequest() : request;
        boolean defaultAddress = value.defaultAddress == null
                ? userAddresses(account).isEmpty()
                : value.defaultAddress;
        Address address = new Address(
                ids.incrementAndGet(),
                account.id,
                requireText(value.receiver, "收货人不能为空"),
                maskPhoneInput(value.phone),
                requireText(value.detail, "收货地址不能为空"),
                normalizeDistance(value.distanceKm),
                value.inRange == null ? normalizeDistance(value.distanceKm) <= 6.0 : value.inRange,
                defaultAddress);
        addresses.put(address.id, address);
        if (address.defaultAddress) {
            makeDefaultAddress(account, address.id);
        } else {
            persistAddress(address);
        }
        audit(account, "CREATE_ADDRESS", "ADDRESS", address.id, "-", "CREATED", "新增收货地址");
        return address;
    }

    public synchronized Address updateAddress(Account account, long addressId, AddressRequest request) {
        Address address = requireAddress(account, addressId);
        AddressRequest value = request == null ? new AddressRequest() : request;
        String before = address.receiver + "/" + address.detailMasked + "/" + address.defaultAddress;
        if (value.receiver != null) {
            address.receiver = requireText(value.receiver, "收货人不能为空");
        }
        if (value.phone != null) {
            address.phoneMasked = maskPhoneInput(value.phone);
        }
        if (value.detail != null) {
            address.detailMasked = requireText(value.detail, "收货地址不能为空");
        }
        if (value.distanceKm != null) {
            address.distanceKm = normalizeDistance(value.distanceKm);
            if (value.inRange == null) {
                address.inRange = address.distanceKm <= 6.0;
            }
        }
        if (value.inRange != null) {
            address.inRange = value.inRange;
        }
        if (Boolean.TRUE.equals(value.defaultAddress)) {
            makeDefaultAddress(account, address.id);
        } else {
            persistAddress(address);
        }
        audit(account, "UPDATE_ADDRESS", "ADDRESS", address.id, before,
                address.receiver + "/" + address.detailMasked + "/" + address.defaultAddress, "修改收货地址");
        return address;
    }

    public synchronized List<Address> deleteAddress(Account account, long addressId) {
        Address address = requireAddress(account, addressId);
        boolean wasDefault = address.defaultAddress;
        addresses.remove(address.id);
        deleteAddressRecord(address.id);
        List<Address> left = userAddresses(account);
        if (wasDefault && !left.isEmpty()) {
            makeDefaultAddress(account, left.get(0).id);
        }
        audit(account, "DELETE_ADDRESS", "ADDRESS", address.id,
                address.detailMasked, "DELETED", "删除收货地址");
        return userAddresses(account);
    }

    public synchronized Address setDefaultAddress(Account account, long addressId) {
        Address address = requireAddress(account, addressId);
        makeDefaultAddress(account, address.id);
        audit(account, "SET_DEFAULT_ADDRESS", "ADDRESS", address.id, "-", "DEFAULT", "设置默认地址");
        return address;
    }

    public synchronized MarketHome marketHome() {
        List<Store> recommendedStores = stores(null, null, "default").stream()
                .limit(8)
                .toList();
        List<Product> hotProducts = products.values().stream()
                .filter(Product::available)
                .sorted(Comparator.comparing((Product product) -> product.monthlySales).reversed())
                .limit(10)
                .toList();
        return new MarketHome(channels, banners, recommendedStores, hotProducts);
    }

    public synchronized List<Store> stores() {
        return stores(null, null, "default");
    }

    public synchronized StorePage storesPage(String keyword, String category, String sort, int page, int size) {
        int pageValue = Math.max(0, page);
        int sizeValue = Math.max(5, Math.min(30, size <= 0 ? 12 : size));
        String cacheKey = String.join("|",
                Objects.toString(keyword, ""),
                Objects.toString(category, ""),
                Objects.toString(sort, "default"),
                String.valueOf(pageValue),
                String.valueOf(sizeValue));
        return storePageCache.get(cacheKey, ignored -> {
            List<Store> allStores = stores(keyword, category, sort);
            int from = Math.min(pageValue * sizeValue, allStores.size());
            int to = Math.min(from + sizeValue, allStores.size());
            return new StorePage(allStores.subList(from, to), pageValue, sizeValue, allStores.size(), to < allStores.size());
        });
    }

    public synchronized MerchantAccountPage merchantAccountsPage(String keyword, int page, int size) {
        String keywordValue = Objects.toString(keyword, "").trim().toLowerCase();
        List<MerchantAccountView> allAccounts = stores.values().stream()
                .map(store -> {
                    Account account = accounts.get(store.merchantId);
                    if (account == null || account.role != Role.MERCHANT) {
                        return null;
                    }
                    return new MerchantAccountView(account.id, account.username, account.displayName, store.id, store.name,
                            store.category, store.area, store.monthlySales, store.open);
                })
                .filter(Objects::nonNull)
                .filter(item -> keywordValue.isBlank()
                        || item.username().toLowerCase().contains(keywordValue)
                        || item.displayName().toLowerCase().contains(keywordValue)
                        || item.storeName().toLowerCase().contains(keywordValue)
                        || item.category().toLowerCase().contains(keywordValue)
                        || item.area().toLowerCase().contains(keywordValue))
                .sorted(Comparator.comparing(MerchantAccountView::storeId))
                .toList();
        PageWindow window = pageWindow(allAccounts.size(), page, size, 10);
        return new MerchantAccountPage(allAccounts.subList(window.from, window.to),
                window.page, window.size, allAccounts.size(), window.to < allAccounts.size());
    }

    public synchronized List<Store> stores(String keyword, String category, String sort) {
        String keywordValue = Objects.toString(keyword, "").trim().toLowerCase();
        String categoryValue = Objects.toString(category, "").trim();
        Comparator<Store> comparator = Comparator
                .comparing((Store store) -> !store.open)
                .thenComparing((Store store) -> store.deliveryPriority, Comparator.reverseOrder());
        if ("rating".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((Store store) -> store.rating).reversed();
        } else if ("distance".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(store -> store.distanceKm);
        } else if ("sales".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((Store store) -> store.monthlySales).reversed();
        }
        return stores.values().stream()
                .filter(store -> keywordValue.isBlank()
                        || store.name.toLowerCase().contains(keywordValue)
                        || store.tags.stream().anyMatch(tag -> tag.toLowerCase().contains(keywordValue))
                        || products.values().stream().anyMatch(product -> product.storeId == store.id && product.name.toLowerCase().contains(keywordValue)))
                .filter(store -> categoryValue.isBlank()
                        || "全部".equals(categoryValue)
                        || store.category.equals(categoryValue)
                        || store.tags.contains(categoryValue))
                .sorted(comparator)
                .toList();
    }

    public synchronized List<Product> products(long storeId) {
        requireStore(storeId);
        return products.values().stream()
                .filter(product -> product.storeId == storeId)
                .sorted(Comparator.comparing(Product::available).reversed().thenComparing(product -> product.id))
                .toList();
    }

    public synchronized Store storeDetail(long storeId) {
        return requireStore(storeId);
    }

    public synchronized List<CartItem> cartItems(Account account) {
        List<CartItem> items = carts.computeIfAbsent(account.id, ignored -> new ArrayList<>());
        items.forEach(this::refreshCartItem);
        return items;
    }

    public synchronized List<CartItem> addCartItem(Account account, AddCartItemRequest request) {
        Product product = requireProduct(request.productId);
        if (!product.available()) {
            throw new BusinessException(HttpStatus.CONFLICT, "商品已下架或库存不足");
        }
        int quantity = Math.max(1, request.quantity);
        List<CartItem> items = carts.computeIfAbsent(account.id, ignored -> new ArrayList<>());
        CartItem item = items.stream()
                .filter(current -> current.productId == request.productId)
                .findFirst()
                .orElse(null);
        if (item == null) {
            item = new CartItem(ids.incrementAndGet(), request.productId, product.storeId, product.name, product.price, quantity);
            items.add(item);
        } else {
            item.quantity += quantity;
        }
        refreshCartItem(item);
        persistCartItem(account.id, item);
        return cartItems(account);
    }

    public synchronized List<CartItem> removeCartItem(Account account, long productId) {
        carts.computeIfAbsent(account.id, ignored -> new ArrayList<>())
                .removeIf(item -> item.productId == productId);
        deleteCartItemRecord(account.id, productId);
        return cartItems(account);
    }

    public synchronized Order createOrder(Account account, CreateOrderRequest request) {
        if (request.idempotencyToken == null || request.idempotencyToken.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少幂等 Token");
        }
        String idempotencyKey = account.id + ":" + request.idempotencyToken;
        Long existedOrderId = idempotentOrders.get(idempotencyKey);
        if (existedOrderId != null) {
            return orders.get(existedOrderId);
        }

        Address address = requireAddress(account, request.addressId);
        if (!address.inRange) {
            throw new BusinessException(HttpStatus.CONFLICT, "收货地址不在配送范围内");
        }

        List<CreateOrderItem> sourceItems = normalizeOrderItems(account, request);
        if (sourceItems.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单商品不能为空");
        }

        long storeId = request.storeId == null ? requireProduct(sourceItems.get(0).productId).storeId : request.storeId;
        Store store = requireStore(storeId);
        if (!store.open) {
            throw new BusinessException(HttpStatus.CONFLICT, "门店当前未营业");
        }

        BigDecimal itemAmount = ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderItem itemRequest : sourceItems) {
            Product product = requireProduct(itemRequest.productId);
            if (product.storeId != storeId) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "一个订单只能包含同一门店商品");
            }
            if (!product.available()) {
                throw new BusinessException(HttpStatus.CONFLICT, product.name + " 已下架或库存不足");
            }
            if (itemRequest.quantity <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品数量必须大于 0");
            }
            if (product.stock < itemRequest.quantity) {
                throw new BusinessException(HttpStatus.CONFLICT, product.name + " 库存不足");
            }
            BigDecimal subtotal = product.price.multiply(BigDecimal.valueOf(itemRequest.quantity)).setScale(2, RoundingMode.HALF_UP);
            itemAmount = itemAmount.add(subtotal);
            orderItems.add(new OrderItem(product.id, product.name, product.price, itemRequest.quantity, subtotal));
        }

        if (itemAmount.compareTo(store.minDeliveryAmount) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "未达到门店起送价 " + store.minDeliveryAmount + " 元");
        }

        UserCoupon selectedCoupon = resolveUsableCoupon(account, itemAmount, request.couponCode);
        String couponCode = selectedCoupon == null ? "" : selectedCoupon.couponCode;
        BigDecimal discountAmount = discount(itemAmount, selectedCoupon);
        BigDecimal payAmount = itemAmount.add(store.deliveryFee).subtract(discountAmount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        long orderId = ids.incrementAndGet();
        Order order = new Order();
        order.id = orderId;
        order.orderNo = "TO" + orderId;
        order.customerId = account.id;
        order.customerName = account.displayName;
        order.storeId = store.id;
        order.storeName = store.name;
        order.status = OrderStatus.WAIT_PAY;
        order.itemAmount = itemAmount;
        order.deliveryFee = store.deliveryFee;
        order.discountAmount = discountAmount;
        order.payAmount = payAmount;
        order.idempotencyToken = request.idempotencyToken;
        order.couponCode = selectedCoupon == null ? null : selectedCoupon.couponCode;
        order.createdAt = LocalDateTime.now();
        order.estimatedDeliveryMinutes = store.avgDeliveryMinutes;
        order.addressSnapshot = new AddressSnapshot(address.receiver, address.phoneMasked, address.detailMasked, address.distanceKm);
        order.items.addAll(orderItems);
        appendStatus(order, null, OrderStatus.WAIT_PAY, "SYSTEM", "创建订单");

        lockCouponHold(account, order);
        List<InventoryReservation> orderReservations = new ArrayList<>();
        try {
            for (OrderItem item : orderItems) {
                InventoryReservation reservation = new InventoryReservation(ids.incrementAndGet(), order.id, item.productId, item.productName, item.quantity, "HELD", LocalDateTime.now());
                orderReservations.add(reservation);
            }
        } catch (RuntimeException ex) {
            releaseCouponHold(order);
            throw ex;
        }

        PaymentOrder payment = new PaymentOrder(ids.incrementAndGet(), "PAY" + orderId, order.id, "WAIT_PAY", payAmount, null, null);
        order.paymentOrderNo = payment.paymentNo;
        OutboxEvent orderCreatedEvent = newOutboxEvent("ORDER_CREATED", "ORDER", order.id, "订单已创建，等待支付");
        AuditLog createAudit = newAuditLog(account, "CREATE_ORDER", "ORDER", order.id, "-", order.status.name(), "服务端重算金额并占用库存");

        boolean redisReserved = false;
        try {
            if (databaseEnabled()) {
                if (redisEnabled()) {
                    redisReserved = redisFeatureStore.reserveStockBatch(orderItems);
                    if (!redisReserved) {
                        throw new BusinessException(HttpStatus.CONFLICT, "库存不足，请刷新后重试");
                    }
                }
                boolean saved = databaseStore.saveOrderCreatedTransaction(order, payment, orderReservations, orderCreatedEvent, createAudit);
                if (!saved) {
                    if (redisReserved) {
                        orderItems.forEach(item -> redisFeatureStore.releaseStock(item.productId, item.quantity));
                    }
                    throw new BusinessException(HttpStatus.CONFLICT, "库存不足，请刷新后重试");
                }
                order.items.forEach(this::decreaseMemoryStock);
            } else {
                reserveStock(orderItems);
            }
        } catch (RuntimeException ex) {
            if (redisReserved) {
                orderItems.forEach(item -> redisFeatureStore.releaseStock(item.productId, item.quantity));
            }
            releaseCouponHold(order);
            throw ex;
        }

        payments.put(payment.id, payment);
        orders.put(order.id, order);
        idempotentOrders.put(idempotencyKey, order.id);
        orderReservations.forEach(reservation -> reservations.put(reservation.id, reservation));
        outboxEvents.put(orderCreatedEvent.id, orderCreatedEvent);
        auditLogs.put(createAudit.id, createAudit);
        if (!databaseEnabled()) {
            persistOrderAggregate(order);
            persistPayment(payment);
            orderReservations.forEach(this::persistReservation);
            order.items.forEach(item -> persistProduct(products.get(item.productId)));
            persistOutbox(orderCreatedEvent);
            persistAudit(createAudit);
        }
        carts.computeIfAbsent(account.id, ignored -> new ArrayList<>()).clear();
        deleteCartItems(account.id);
        return order;
    }

    public synchronized CouponClaimView claimCoupon(Account account, String sceneCode, CouponClaimRequest request, String clientIp) {
        String scene = normalizeCouponScene(sceneCode);
        CouponActivity activity = currentCouponActivity(scene);
        boolean captchaOk = captchaPassed(request);
        if (!allowRate("coupon:captcha:" + account.id, 3, 10_000) && !captchaOk) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "需要验证码：请输入 3+5 的结果");
        }
        if (!allowRate("coupon:user:" + account.id, 10, 1000) || !allowRate("coupon:ip:" + clientIp, 100, 60_000)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        List<UserCoupon> existing = userCouponInstances.values().stream()
                .filter(coupon -> coupon.userId == account.id)
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .toList();
        if (!existing.isEmpty()) {
            long remaining = remainingCouponStock(activity);
            outbox("COUPON_CLAIM_DUPLICATE", "COUPON", account.id, activity.batchCode + " 重复领取");
            audit(account, "CLAIM_COUPON", "COUPON", account.id, "-", "ALREADY_CLAIMED", activity.batchCode + " / " + clientIp);
            return new CouponClaimView(scene, activity.batchCode, existing.get(0).couponCode, "ALREADY_CLAIMED",
                    remaining, existing.size(), "本期券包已领取，可在我的优惠券查看");
        }

        long remainingStock;
        String status;
        if (redisEnabled()) {
            RedisFeatureStore.CouponClaimResult result = redisFeatureStore.claimCoupon(account.id, activity.batchCode);
            if ("SOLD_OUT".equals(result.status())) {
                throw new BusinessException(HttpStatus.CONFLICT, "红包已抢完");
            }
            remainingStock = result.remainingStock();
            status = result.status();
        } else {
            Set<Long> claims = memoryCouponClaims.computeIfAbsent(activity.batchCode, ignored -> ConcurrentHashMap.newKeySet());
            if (claims.contains(account.id)) {
                remainingStock = memoryCouponStocks.getOrDefault(activity.batchCode, 0);
                status = "ALREADY_CLAIMED";
            } else {
                int currentStock = memoryCouponStocks.getOrDefault(activity.batchCode, 0);
                if (currentStock <= 0) {
                    throw new BusinessException(HttpStatus.CONFLICT, "红包已抢完");
                }
                claims.add(account.id);
                remainingStock = currentStock - 1L;
                memoryCouponStocks.put(activity.batchCode, (int) remainingStock);
                status = "SUCCESS";
            }
        }

        List<UserCoupon> issuedCoupons = "SUCCESS".equals(status)
                ? issueCouponPack(account, activity)
                : List.of();
        outbox("COUPON_CLAIMED", "COUPON", account.id, activity.batchCode + " 券包领取 " + status + "，发放 " + issuedCoupons.size() + " 张");
        audit(account, "CLAIM_COUPON", "COUPON", account.id, "-", status, activity.batchCode + " / " + clientIp);
        String firstCouponCode = issuedCoupons.isEmpty() ? "" : issuedCoupons.get(0).couponCode;
        return new CouponClaimView(scene, activity.batchCode, firstCouponCode, status, remainingStock,
                issuedCoupons.size(), "SUCCESS".equals(status) ? "券包已领取，已发放 " + issuedCoupons.size() + " 张券" : "本期券包已领取");
    }

    public synchronized List<Order> userOrders(Account account) {
        return orders.values().stream()
                .filter(order -> order.customerId == account.id)
                .sorted(Comparator.comparing((Order order) -> order.createdAt).reversed())
                .toList();
    }

    public synchronized Order userOrder(Account account, long orderId) {
        Order order = requireOrder(orderId);
        if (order.customerId != account.id) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能访问自己的订单");
        }
        return order;
    }

    public synchronized List<ReviewView> userReviews(Account account) {
        return reviews.values().stream()
                .filter(review -> review.customerId == account.id)
                .map(this::reviewView)
                .sorted(Comparator.comparing((ReviewView review) -> review.createdAt()).reversed())
                .toList();
    }

    public synchronized List<TicketView> userTickets(Account account) {
        return tickets.values().stream()
                .filter(ticket -> ticket.customerId == account.id)
                .map(this::ticketView)
                .sorted(Comparator.comparing((TicketView ticket) -> ticket.createdAt()).reversed())
                .toList();
    }

    public synchronized List<ReviewView> storeReviews(long storeId) {
        requireStore(storeId);
        return reviews.values().stream()
                .filter(review -> {
                    Order order = orders.get(review.orderId);
                    return order != null && order.storeId == storeId;
                })
                .map(this::reviewView)
                .sorted(Comparator.comparing((ReviewView review) -> review.createdAt()).reversed())
                .toList();
    }

    public synchronized List<ReviewView> merchantReviews(Account merchant) {
        return reviews.values().stream()
                .filter(review -> {
                    Order order = orders.get(review.orderId);
                    return order != null && merchantOwnsStore(merchant, order.storeId);
                })
                .map(this::reviewView)
                .sorted(Comparator.comparing((ReviewView review) -> review.createdAt()).reversed())
                .toList();
    }

    public synchronized ReviewView replyMerchantReview(Account merchant, long reviewId, ReviewReplyRequest request) {
        Review review = requireReview(reviewId);
        Order order = requireOrder(review.orderId);
        if (!merchantOwnsStore(merchant, order.storeId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能回复自己门店的评价");
        }
        String before = review.merchantReply == null ? "-" : review.merchantReply;
        review.merchantReply = requireText(request == null ? null : request.reply, "回复内容不能为空");
        review.repliedAt = LocalDateTime.now();
        persistReview(review);
        audit(merchant, "REPLY_REVIEW", "REVIEW", review.id, before, review.merchantReply, "商家回复用户评价");
        return reviewView(review);
    }

    public synchronized List<TicketView> merchantTickets(Account merchant) {
        return tickets.values().stream()
                .filter(ticket -> {
                    Order order = orders.get(ticket.orderId);
                    return order != null && merchantOwnsStore(merchant, order.storeId);
                })
                .map(this::ticketView)
                .sorted(Comparator.comparing((TicketView ticket) -> ticket.createdAt()).reversed())
                .toList();
    }

    public synchronized Order payOrder(Account account, long orderId, PayRequest request) {
        Order order = userOrder(account, orderId);
        PaymentOrder payment = paymentByOrder(order.id);
        String callbackFlow = request.callbackFlowNo == null || request.callbackFlowNo.isBlank()
                ? "MOCK-" + payment.paymentNo
                : request.callbackFlowNo;
        if (paymentCallbackFlows.contains(callbackFlow)) {
            return order;
        }
        paymentCallbackFlows.add(callbackFlow);
        if ("PAID".equals(payment.status)) {
            return order;
        }
        transition(order, OrderStatus.PAID_WAIT_ACCEPT, account.displayName, "模拟支付成功");
        payment.status = "PAID";
        payment.callbackFlowNo = callbackFlow;
        payment.paidAt = LocalDateTime.now();
        order.paidAt = payment.paidAt;
        persistPayment(payment);
        persistPaymentCallback(callbackFlow, payment.paymentNo);
        persistOrderAggregate(order);
        outbox("PAYMENT_SUCCEEDED", "ORDER", order.id, "通知商家接单并准备生成配送任务");
        consumeCouponHold(order);
        audit(account, "PAY_ORDER", "PAYMENT", payment.id, "WAIT_PAY", "PAID", "支付回调流水 " + callbackFlow);
        return order;
    }

    public synchronized Order confirmOrder(Account account, long orderId) {
        Order order = userOrder(account, orderId);
        if (order.status != OrderStatus.DELIVERED) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有已送达订单可以确认收货");
        }
        transition(order, OrderStatus.COMPLETED, account.displayName, "用户确认收货");
        consumeReservations(order.id);
        outbox("ORDER_CONFIRMED", "ORDER", order.id, "用户已确认收货，订单完成");
        audit(account, "CONFIRM_ORDER", "ORDER", order.id, "DELIVERED", "COMPLETED", "用户确认收货");
        return order;
    }

    public synchronized Order refundOrder(Account account, long orderId, RefundRequest request) {
        Order order = userOrder(account, orderId);
        String reason = request.reason == null || request.reason.isBlank() ? "用户申请退款" : request.reason;
        if (order.status == OrderStatus.WAIT_PAY) {
            transition(order, OrderStatus.CANCELLED, account.displayName, reason);
            releaseReservations(order.id);
            releaseCouponHold(order);
            audit(account, "CANCEL_UNPAID_ORDER", "ORDER", order.id, "WAIT_PAY", "CANCELLED", reason);
            return order;
        }
        if (order.status == OrderStatus.PAID_WAIT_ACCEPT) {
            RefundOrder refund = createRefund(order, reason, "SUCCESS");
            transition(order, OrderStatus.REFUNDED, account.displayName, reason);
            releaseReservations(order.id);
            releaseCouponHold(order);
            audit(account, "AUTO_REFUND", "REFUND", refund.id, "PROCESSING", "SUCCESS", reason);
            return order;
        }
        RefundOrder refund = createRefund(order, reason, "PROCESSING");
        Ticket ticket = new Ticket(ids.incrementAndGet(), order.id, account.id, "PENDING", reason, "等待后台审核", LocalDateTime.now(), null);
        tickets.put(ticket.id, ticket);
        persistTicket(ticket);
        transition(order, OrderStatus.REFUNDING, account.displayName, reason);
        audit(account, "CREATE_AFTERSALE_TICKET", "TICKET", ticket.id, "-", "PENDING", "退款单 " + refund.refundNo);
        return order;
    }

    public synchronized Review reviewOrder(Account account, long orderId, ReviewRequest request) {
        Order order = userOrder(account, orderId);
        if (order.status != OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "确认收货后才能评价");
        }
        if (order.review != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单已评价");
        }
        int score = Math.max(1, Math.min(5, request.score));
        Review review = new Review(ids.incrementAndGet(), order.id, account.id, score, request.content, LocalDateTime.now());
        reviews.put(review.id, review);
        order.review = review;
        persistReview(review);
        persistOrderAggregate(order);
        audit(account, "REVIEW_ORDER", "ORDER", order.id, "-", String.valueOf(score), "用户评价");
        return review;
    }

    @Scheduled(fixedDelayString = "${app.orders.unpaid-timeout-scan-ms:60000}")
    public void closeExpiredWaitPayOrders() {
        closeExpiredWaitPayOrders(LocalDateTime.now(), UNPAID_ORDER_TIMEOUT_MINUTES);
    }

    public synchronized int closeExpiredWaitPayOrders(LocalDateTime now, int timeoutMinutes) {
        LocalDateTime deadline = now.minusMinutes(Math.max(1, timeoutMinutes));
        List<Order> expiredOrders = orders.values().stream()
                .filter(order -> order.status == OrderStatus.WAIT_PAY)
                .filter(order -> order.createdAt != null && order.createdAt.isBefore(deadline))
                .toList();
        for (Order order : expiredOrders) {
            PaymentOrder payment = paymentByOrder(order.id);
            if ("WAIT_PAY".equals(payment.status)) {
                payment.status = "CLOSED";
                persistPayment(payment);
            }
            transition(order, OrderStatus.CANCELLED, "SYSTEM", "待支付超时自动关闭");
            releaseReservations(order.id);
            releaseCouponHold(order);
            outbox("ORDER_UNPAID_TIMEOUT_CLOSED", "ORDER", order.id, "待支付订单超时关闭，库存占用已释放");
            audit(systemActor(), "CLOSE_UNPAID_ORDER", "ORDER", order.id, "WAIT_PAY", "CANCELLED", "待支付超时自动关闭");
        }
        return expiredOrders.size();
    }

    public synchronized Store merchantStore(Account merchant) {
        return requireStore(requireMerchantStoreId(merchant));
    }

    public synchronized Store updateMerchantStore(Account merchant, StoreConfigRequest request) {
        Store store = merchantStore(merchant);
        StoreConfigRequest value = request == null ? new StoreConfigRequest() : request;
        String before = store.open + "/" + store.minDeliveryAmount + "/" + store.deliveryFee + "/" + store.businessHours;
        if (value.name != null) {
            store.name = requireText(value.name, "门店名称不能为空");
        }
        if (value.notice != null) {
            store.notice = requireText(value.notice, "门店公告不能为空");
        }
        if (value.statusMessage != null) {
            store.statusMessage = requireText(value.statusMessage, "履约说明不能为空");
        }
        if (value.open != null) {
            store.open = value.open;
        }
        if (value.minDeliveryAmount != null) {
            store.minDeliveryAmount = money(value.minDeliveryAmount);
        }
        if (value.deliveryFee != null) {
            store.deliveryFee = money(value.deliveryFee);
        }
        if (value.avgDeliveryMinutes != null) {
            store.avgDeliveryMinutes = Math.max(10, Math.min(120, value.avgDeliveryMinutes));
        }
        if (value.deliveryRangeKm != null) {
            store.deliveryRangeKm = Math.max(0.5, Math.min(30.0, value.deliveryRangeKm));
        }
        if (value.businessHours != null) {
            store.businessHours = requireText(value.businessHours, "营业时间不能为空");
        }
        if (value.deliveryGuarantee != null) {
            store.deliveryGuarantee = requireText(value.deliveryGuarantee, "配送保障不能为空");
        }
        if (value.tags != null && !value.tags.isEmpty()) {
            store.tags = normalizeStringList(value.tags, 8);
        }
        if (value.promotions != null) {
            store.promotions = normalizeStringList(value.promotions, 6);
        }
        if (value.couponHints != null) {
            store.couponHints = normalizeStringList(value.couponHints, 6);
        }
        persistStore(store);
        audit(merchant, "UPDATE_STORE", "STORE", store.id, before,
                store.open + "/" + store.minDeliveryAmount + "/" + store.deliveryFee + "/" + store.businessHours,
                "商家维护门店经营配置");
        return store;
    }

    public synchronized Store setMerchantStoreOpen(Account merchant, boolean open) {
        Store store = merchantStore(merchant);
        boolean before = store.open;
        store.open = open;
        persistStore(store);
        audit(merchant, open ? "OPEN_STORE" : "CLOSE_STORE", "STORE", store.id,
                String.valueOf(before), String.valueOf(open), open ? "商家开店营业" : "商家打烊");
        return store;
    }

    public synchronized List<Product> merchantProducts(Account merchant) {
        return products.values().stream()
                .filter(product -> merchantOwnsStore(merchant, product.storeId))
                .sorted(Comparator.comparing((Product product) -> product.storeId).thenComparing(product -> product.id))
                .toList();
    }

    public synchronized ProductPage merchantProductsPage(Account merchant, int page, int size) {
        List<Product> allProducts = merchantProducts(merchant);
        PageWindow window = pageWindow(allProducts.size(), page, size, 12);
        return new ProductPage(allProducts.subList(window.from, window.to), window.page, window.size, allProducts.size(), window.to < allProducts.size());
    }

    public synchronized Product saveMerchantProduct(Account merchant, MerchantProductRequest request) {
        long storeId = requireMerchantStoreId(merchant);
        MerchantProductRequest value = request == null ? new MerchantProductRequest() : request;
        Product product;
        if (value.id == null) {
            product = new Product(ids.incrementAndGet(), storeId,
                    requireText(value.name, "商品名不能为空"),
                    requireText(value.description, "商品描述不能为空"),
                    money(value.price),
                    Math.max(0, value.stock),
                    value.onSale,
                    0,
                    defaultText(value.category, "商家新品"),
                    defaultText(value.imageTone, "default"),
                    99,
                    defaultText(value.discountLabel, "新品"),
                    money(value.originalPrice == null || value.originalPrice.isBlank() ? value.price : value.originalPrice));
            products.put(product.id, product);
            audit(merchant, "CREATE_PRODUCT", "PRODUCT", product.id, "-", "CREATED", "商家新增商品");
        } else {
            product = requireProduct(value.id);
            ensureMerchantProduct(merchant, product);
            String before = product.stock + "/" + product.onSale;
            product.name = requireText(value.name, "商品名不能为空");
            product.description = requireText(value.description, "商品描述不能为空");
            product.price = money(value.price);
            product.stock = Math.max(0, value.stock);
            product.onSale = value.onSale;
            product.category = defaultText(value.category, product.category);
            product.imageTone = defaultText(value.imageTone, product.imageTone);
            product.discountLabel = defaultText(value.discountLabel, product.discountLabel);
            product.originalPrice = money(value.originalPrice == null || value.originalPrice.isBlank()
                    ? value.price
                    : value.originalPrice);
            audit(merchant, "UPDATE_PRODUCT", "PRODUCT", product.id, before, product.stock + "/" + product.onSale, "商家维护商品");
        }
        persistProduct(product);
        return product;
    }

    public synchronized Product updateMerchantProduct(Account merchant, long productId, MerchantProductRequest request) {
        request.id = productId;
        return saveMerchantProduct(merchant, request);
    }

    public synchronized List<Order> merchantPendingOrders(Account merchant) {
        return orders.values().stream()
                .filter(order -> merchantOwnsStore(merchant, order.storeId))
                .filter(order -> order.status == OrderStatus.PAID_WAIT_ACCEPT)
                .sorted(Comparator.comparing((Order order) -> order.createdAt))
                .toList();
    }

    public synchronized List<Order> merchantOrders(Account merchant) {
        return orders.values().stream()
                .filter(order -> merchantOwnsStore(merchant, order.storeId))
                .sorted(Comparator.comparing((Order order) -> order.createdAt).reversed())
                .toList();
    }

    public synchronized OrderPage merchantOrdersPage(Account merchant, int page, int size) {
        List<Order> allOrders = merchantOrders(merchant);
        PageWindow window = pageWindow(allOrders.size(), page, size, 8);
        return new OrderPage(allOrders.subList(window.from, window.to), window.page, window.size, allOrders.size(), window.to < allOrders.size());
    }

    public synchronized Order merchantAccept(Account merchant, long orderId) {
        Order order = requireMerchantOrder(merchant, orderId);
        transition(order, OrderStatus.PREPARING, merchant.displayName, "商家接单");
        outbox("MERCHANT_ACCEPTED", "ORDER", order.id, "通知用户商家已接单");
        audit(merchant, "ACCEPT_ORDER", "ORDER", order.id, "PAID_WAIT_ACCEPT", order.status.name(), "商家接单");
        return order;
    }

    public synchronized Order merchantReject(Account merchant, long orderId, RefundRequest request) {
        Order order = requireMerchantOrder(merchant, orderId);
        if (order.status != OrderStatus.PAID_WAIT_ACCEPT) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有待接单订单可以拒单");
        }
        String reason = request.reason == null || request.reason.isBlank() ? "商家拒单自动退款" : request.reason;
        RefundOrder refund = createRefund(order, reason, "SUCCESS");
        transition(order, OrderStatus.REFUNDED, merchant.displayName, reason);
        releaseReservations(order.id);
        releaseCouponHold(order);
        outbox("ORDER_REJECTED_REFUNDED", "ORDER", order.id, "通知用户退款成功");
        audit(merchant, "REJECT_ORDER", "REFUND", refund.id, "PAID_WAIT_ACCEPT", "REFUNDED", reason);
        return order;
    }

    public synchronized Order merchantReady(Account merchant, long orderId) {
        Order order = requireMerchantOrder(merchant, orderId);
        transition(order, OrderStatus.WAIT_PICKUP, merchant.displayName, "商家出餐");
        DeliveryOrder delivery = deliveryByOrder(order.id);
        boolean created = false;
        if (delivery == null) {
            delivery = new DeliveryOrder(ids.incrementAndGet(), order.id, null, null, "WAIT_ACCEPT", "商家已出餐，等待骑手接单", null, LocalDateTime.now(), null);
            deliveries.put(delivery.id, delivery);
            order.deliveryId = delivery.id;
            created = true;
        } else if (order.deliveryId == null) {
            order.deliveryId = delivery.id;
        }
        persistDelivery(delivery);
        persistOrderAggregate(order);
        if (created) {
            appendDeliveryLog(delivery, null, "WAIT_ACCEPT", merchant.displayName, "商家出餐后进入骑手可接任务池", "STORE");
        }
        outbox("DELIVERY_TASK_CREATED", "DELIVERY", delivery.id, "配送任务进入骑手可接池");
        audit(merchant, "MARK_ORDER_READY", "DELIVERY", delivery.id, "PREPARING", "WAIT_ACCEPT", "商家出餐");
        return order;
    }

    public synchronized List<DeliveryOrder> availableRiderTasks(Account rider) {
        return deliveries.values().stream()
                .filter(delivery -> "WAIT_ACCEPT".equals(delivery.status))
                .filter(delivery -> delivery.riderId == null)
                .sorted(Comparator.comparing((DeliveryOrder delivery) -> delivery.createdAt))
                .toList();
    }

    public synchronized List<DeliveryOrder> riderTasks(Account rider) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.riderId != null && delivery.riderId == rider.id)
                .sorted(Comparator.comparing((DeliveryOrder delivery) -> delivery.createdAt).reversed())
                .toList();
    }

    public synchronized DeliveryOrder riderAccept(Account rider, long taskId) {
        DeliveryOrder delivery = deliveries.get(taskId);
        if (delivery == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "配送任务不存在");
        }
        if (!"WAIT_ACCEPT".equals(delivery.status) || delivery.riderId != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "该任务已被其他骑手接走");
        }
        String before = delivery.status;
        delivery.riderId = rider.id;
        delivery.riderName = rider.displayName;
        delivery.status = "ACCEPTED";
        delivery.currentStep = "骑手已接单，前往商家";
        appendDeliveryLog(delivery, before, delivery.status, rider.displayName, "骑手接单", "RIDER_APP");
        persistDelivery(delivery);
        outbox("RIDER_ACCEPTED_DELIVERY", "DELIVERY", delivery.id, "骑手 " + rider.displayName + " 已接单");
        audit(rider, "ACCEPT_DELIVERY", "DELIVERY", delivery.id, before, delivery.status, "骑手接单");
        return delivery;
    }

    public synchronized DeliveryOrder riderArriveStore(Account rider, long taskId) {
        DeliveryOrder delivery = requireRiderTask(rider, taskId);
        if (!"ACCEPTED".equals(delivery.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有已接单任务可以确认到店");
        }
        String before = delivery.status;
        delivery.status = "ARRIVED_STORE";
        delivery.currentStep = "骑手已到店，等待核对餐品";
        appendDeliveryLog(delivery, before, delivery.status, rider.displayName, "骑手到店", "STORE");
        persistDelivery(delivery);
        outbox("RIDER_ARRIVED_STORE", "DELIVERY", delivery.id, "骑手已到店");
        audit(rider, "ARRIVE_STORE", "DELIVERY", delivery.id, before, delivery.status, "骑手到店");
        return delivery;
    }

    public synchronized DeliveryOrder riderPickup(Account rider, long taskId) {
        DeliveryOrder delivery = requireRiderTask(rider, taskId);
        if (!"ARRIVED_STORE".equals(delivery.status) && !"WAIT_PICKUP".equals(delivery.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "请先确认到店后再取餐");
        }
        Order order = requireOrder(delivery.orderId);
        transition(order, OrderStatus.DELIVERING, rider.displayName, "骑手确认取餐");
        String before = delivery.status;
        delivery.status = "DELIVERING";
        delivery.currentStep = "配送中";
        appendDeliveryLog(delivery, before, delivery.status, rider.displayName, "骑手确认取餐", "STORE");
        persistDelivery(delivery);
        outbox("RIDER_PICKED_UP", "DELIVERY", delivery.id, "通知用户骑手已取餐");
        audit(rider, "PICKUP_ORDER", "DELIVERY", delivery.id, before, "DELIVERING", "骑手确认取餐");
        return delivery;
    }

    public synchronized DeliveryOrder riderDeliver(Account rider, long taskId) {
        DeliveryOrder delivery = requireRiderTask(rider, taskId);
        Order order = requireOrder(delivery.orderId);
        transition(order, OrderStatus.DELIVERED, rider.displayName, "骑手确认送达");
        String before = delivery.status;
        delivery.status = "DELIVERED";
        delivery.currentStep = "已送达，等待用户确认或评价";
        delivery.deliveredAt = LocalDateTime.now();
        appendDeliveryLog(delivery, before, delivery.status, rider.displayName, "骑手确认送达", "CUSTOMER_ADDRESS");
        persistDelivery(delivery);
        outbox("ORDER_DELIVERED", "ORDER", order.id, "通知用户订单已送达");
        audit(rider, "DELIVER_ORDER", "DELIVERY", delivery.id, before, "DELIVERED", "骑手确认送达");
        return delivery;
    }

    public synchronized RiskRecord riderException(Account rider, long taskId, RiderExceptionRequest request) {
        DeliveryOrder delivery = requireRiderTask(rider, taskId);
        if ("DELIVERED".equals(delivery.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "已送达任务不能上报异常");
        }
        if ("EXCEPTION".equals(delivery.status) || (delivery.exceptionReason != null && !delivery.exceptionReason.isBlank())) {
            throw new BusinessException(HttpStatus.CONFLICT, "该任务已上报异常，请等待平台处理");
        }
        String type = Objects.toString(request.type, "").trim();
        String detail = Objects.toString(request.detail, "").trim();
        if (type.isBlank() && detail.isBlank() && (request.reason == null || request.reason.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写异常类型和异常说明");
        }
        String reason = request.reason == null || request.reason.isBlank()
                ? type + "：" + detail
                : request.reason;
        String evidenceNo = Objects.toString(request.evidenceNo, "").trim();
        if (!evidenceNo.isBlank() && !reason.contains(evidenceNo)) {
            reason = reason + "（凭证 " + evidenceNo + "）";
        }
        String before = delivery.status;
        delivery.status = "EXCEPTION";
        delivery.currentStep = "配送异常待平台处理";
        delivery.exceptionReason = reason;
        RiskRecord record = new RiskRecord(ids.incrementAndGet(), "DELIVERY_EXCEPTION", "DELIVERY", delivery.id, reason, "OPEN", LocalDateTime.now());
        riskRecords.put(record.id, record);
        appendDeliveryLog(delivery, before, delivery.status, rider.displayName, reason, "RIDER_APP");
        persistDelivery(delivery);
        persistRisk(record);
        outbox("DELIVERY_EXCEPTION_REPORTED", "DELIVERY", delivery.id, reason);
        audit(rider, "REPORT_DELIVERY_EXCEPTION", "RISK", record.id, "-", "OPEN", reason);
        return record;
    }

    public synchronized List<Order> adminOrders() {
        return orders.values().stream()
                .sorted(Comparator.comparing((Order order) -> order.createdAt).reversed())
                .toList();
    }

    public synchronized List<Ticket> adminTickets() {
        return tickets.values().stream()
                .sorted(Comparator.comparing((Ticket ticket) -> ticket.createdAt).reversed())
                .toList();
    }

    public synchronized Ticket adminApproveTicket(Account admin, long ticketId, AdminTicketRequest request) {
        Ticket ticket = requireTicket(ticketId);
        Order order = requireOrder(ticket.orderId);
        RefundOrder refund = refundByOrder(order.id);
        String result = request.result == null || request.result.isBlank() ? "后台审核通过，退款成功" : request.result;
        if (order.status == OrderStatus.REFUNDING || order.status == OrderStatus.AFTERSALE) {
            transition(order, OrderStatus.REFUNDED, admin.displayName, result);
        }
        if (refund != null) {
            refund.status = "SUCCESS";
            refund.finishedAt = LocalDateTime.now();
        }
        ticket.status = "APPROVED";
        ticket.result = result;
        ticket.finishedAt = LocalDateTime.now();
        releaseReservations(order.id);
        releaseCouponHold(order);
        persistRefund(refund);
        persistTicket(ticket);
        persistOrderAggregate(order);
        outbox("REFUND_APPROVED", "TICKET", ticket.id, "通知用户退款审核通过");
        audit(admin, "APPROVE_TICKET", "TICKET", ticket.id, "PENDING", "APPROVED", result);
        return ticket;
    }

    public synchronized Ticket adminRejectTicket(Account admin, long ticketId, AdminTicketRequest request) {
        Ticket ticket = requireTicket(ticketId);
        Order order = requireOrder(ticket.orderId);
        String result = request.result == null || request.result.isBlank() ? "后台审核驳回，订单进入售后跟进" : request.result;
        if (order.status == OrderStatus.REFUNDING) {
            transition(order, OrderStatus.AFTERSALE, admin.displayName, result);
        }
        ticket.status = "REJECTED";
        ticket.result = result;
        ticket.finishedAt = LocalDateTime.now();
        persistTicket(ticket);
        persistOrderAggregate(order);
        audit(admin, "REJECT_TICKET", "TICKET", ticket.id, "PENDING", "REJECTED", result);
        return ticket;
    }

    public synchronized List<RiskRecord> adminRiskRecords() {
        return riskRecords.values().stream()
                .sorted(Comparator.comparing((RiskRecord record) -> record.createdAt).reversed())
                .toList();
    }

    public synchronized List<AuditLog> adminAuditLogs() {
        return auditLogs.values().stream()
                .sorted(Comparator.comparing((AuditLog log) -> log.createdAt).reversed())
                .toList();
    }

    public synchronized List<CouponActivityView> adminCouponActivities() {
        userCouponInstances.values().forEach(this::refreshCouponStatus);
        return couponActivities.values().stream()
                .map(this::couponActivityView)
                .toList();
    }

    public synchronized AdminCouponPage adminCoupons(String status, String keyword, int page, int size) {
        userCouponInstances.values().forEach(this::refreshCouponStatus);
        String statusValue = Objects.toString(status, "").trim().toUpperCase();
        String keywordValue = Objects.toString(keyword, "").trim().toLowerCase();
        List<AdminCouponView> allCoupons = userCouponInstances.values().stream()
                .filter(coupon -> statusValue.isBlank() || coupon.status.equalsIgnoreCase(statusValue))
                .filter(coupon -> keywordValue.isBlank() || couponMatchesKeyword(coupon, keywordValue))
                .sorted(Comparator.comparing((UserCoupon coupon) -> coupon.updatedAt).reversed())
                .map(this::adminCouponView)
                .toList();
        PageWindow window = pageWindow(allCoupons.size(), page, size, 10);
        return new AdminCouponPage(allCoupons.subList(window.from, window.to), window.page, window.size,
                allCoupons.size(), window.to < allCoupons.size());
    }

    public synchronized List<CouponStatusLogView> adminCouponStatusLogs(Long couponId, int limit) {
        int max = Math.max(10, Math.min(200, limit <= 0 ? 50 : limit));
        return couponStatusLogs.values().stream()
                .filter(log -> couponId == null || log.couponId == couponId)
                .sorted(Comparator.comparing((CouponStatusLog log) -> log.createdAt).reversed())
                .limit(max)
                .map(this::couponStatusLogView)
                .toList();
    }

    public synchronized DemoSnapshot snapshot() {
        DemoSnapshot snapshot = new DemoSnapshot();
        snapshot.accounts = accounts.values().stream()
                .map(account -> new AccountView(account.id, account.username, account.displayName, account.role, account.storeId))
                .toList();
        snapshot.stores = stores();
        snapshot.products = products.values().stream().toList();
        snapshot.orders = adminOrders();
        snapshot.deliveries = deliveries.values().stream().toList();
        snapshot.reservations = reservations.values().stream().toList();
        snapshot.outboxEvents = outboxEvents.values().stream()
                .sorted(Comparator.comparing((OutboxEvent event) -> event.createdAt).reversed())
                .toList();
        snapshot.outboxConsumeRecords = outboxConsumeRecords.values().stream()
                .sorted(Comparator.comparing((OutboxConsumeRecord record) -> record.consumedAt).reversed())
                .toList();
        snapshot.merchantNotifications = merchantNotifications.values().stream()
                .sorted(Comparator.comparing((MerchantNotification notification) -> notification.createdAt).reversed())
                .toList();
        snapshot.auditLogs = adminAuditLogs();
        snapshot.deliveryStatusLogs = deliveryStatusLogs.values().stream()
                .sorted(Comparator.comparing((DeliveryStatusLog log) -> log.createdAt).reversed())
                .toList();
        return snapshot;
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatch-delay-ms:15000}")
    public synchronized void dispatchOutboxOnce() {
        outboxEvents.values().stream()
                .filter(event -> "PENDING".equals(event.status) || "RETRY".equals(event.status))
                .limit(50)
                .forEach(event -> {
                    boolean published = outboxPublisher == null || outboxPublisher.publish(event);
                    if (published) {
                        consumeOutboxEventIdempotently(event);
                        event.retryCount++;
                        event.status = "PUBLISHED";
                        persistOutbox(event);
                    } else {
                        event.retryCount++;
                        event.status = event.retryCount >= 5 ? "FAILED" : "RETRY";
                        persistOutbox(event);
                    }
                });
    }

    private void consumeOutboxEventIdempotently(OutboxEvent event) {
        if (!"ORDER_CREATED".equals(event.eventType)) {
            return;
        }
        Order order = orders.get(event.aggregateId);
        if (order == null) {
            return;
        }
        String consumeKey = MERCHANT_ORDER_CREATED_CONSUMER + ":" + event.id;
        LocalDateTime consumedAt = LocalDateTime.now();
        MerchantNotification notification = new MerchantNotification(
                ids.incrementAndGet(),
                event.id,
                order.id,
                order.storeId,
                "新订单 " + order.orderNo,
                order.storeName + " 收到新订单，待支付金额 " + order.payAmount + " 元",
                consumedAt);
        if (databaseEnabled()) {
            boolean inserted = databaseStore.saveMerchantNotificationIfNew(event, MERCHANT_ORDER_CREATED_CONSUMER, notification);
            if (!inserted) {
                return;
            }
        } else if (outboxConsumeRecords.containsKey(consumeKey)) {
            return;
        }
        outboxConsumeRecords.put(consumeKey, new OutboxConsumeRecord(
                event.id,
                MERCHANT_ORDER_CREATED_CONSUMER,
                event.eventType,
                event.aggregateType,
                event.aggregateId,
                "CONSUMED",
                consumedAt));
        merchantNotifications.put(notification.id, notification);
    }

    private void seed() {
        Account customer = new Account(1, "customer", passwordHash("123456"), "顾客小王", Role.CUSTOMER, null, "13800001111");
        Account merchant = new Account(2, "merchant_chuanwei", passwordHash("123456"), "川味厨房商家", Role.MERCHANT, 101L, "13800002222");
        Account rider = new Account(3, "rider", passwordHash("123456"), "骑手小李", Role.RIDER, null, "13800003333");
        Account admin = new Account(4, "admin", passwordHash("123456"), "平台管理员", Role.ADMIN, null, "13800004444");
        Account merchant102 = new Account(5, "merchant_qingshi", passwordHash("123456"), "轻食研究所商家", Role.MERCHANT, 102L, "13800005555");
        Account merchant103 = new Account(6, "merchant_apo_noodle", passwordHash("123456"), "阿婆面馆商家", Role.MERCHANT, 103L, "13800006666");
        Account merchant104 = new Account(7, "merchant_panda_tea", passwordHash("123456"), "熊猫茶事商家", Role.MERCHANT, 104L, "13800007777");
        Account merchant105 = new Account(8, "merchant_bianlifeng", passwordHash("123456"), "便利蜂速达商家", Role.MERCHANT, 105L, "13800008888");
        Account merchant106 = new Account(9, "merchant_fruit", passwordHash("123456"), "鲜果切仓商家", Role.MERCHANT, 106L, "13800009999");
        Account merchant107 = new Account(10, "merchant_bbq", passwordHash("123456"), "深夜烧烤局商家", Role.MERCHANT, 107L, "13800001010");
        Account merchant108 = new Account(11, "merchant_errand", passwordHash("123456"), "城市跑腿站商家", Role.MERCHANT, 108L, "13800001108");
        List.of(customer, merchant, rider, admin, merchant102, merchant103, merchant104, merchant105, merchant106, merchant107, merchant108)
                .forEach(account -> accounts.put(account.id, account));

        addresses.put(501L, new Address(501, customer.id, "王同学", maskPhone(customer.phone), "上海市浦东新区张江路***号", 2.6, true, true));
        addresses.put(502L, new Address(502, customer.id, "王同学", maskPhone(customer.phone), "上海市徐汇区漕溪北路***号", 8.8, false, false));

        channels.addAll(List.of(
                new Channel("美食", "热卖正餐", "FOOD"),
                new Channel("甜点饮品", "咖啡奶茶", "DRINK"),
                new Channel("超市便利", "日用品速达", "MARKET"),
                new Channel("水果买菜", "生鲜蔬果", "FRESH"),
                new Channel("夜宵", "深夜营业", "NIGHT"),
                new Channel("品牌连锁", "稳定履约", "CHAIN"),
                new Channel("品质联盟", "高分严选", "QUALITY"),
                new Channel("跑腿代购", "即时帮买", "ERRAND")
        ));
        banners.addAll(List.of(
                new Banner("会员红包日", "今日批次可领 6 张券，独立有效期和使用状态", "领券包", "MEMBER"),
                new Banner("30 分钟必达专区", "每日专区券，高峰期优先调度，超时补偿", "领专区券", "FAST"),
                new Banner("企业午餐", "工作日企业订餐券，统一开票、预算管控", "领企业券", "B2B")
        ));

        stores.put(101L, new Store(101, merchant.id, "川味厨房", "满 60 减 8，30 分钟内送达", true, money("20"), DELIVERY_FEE, 4.8, List.of("川湘菜", "热销", "可开发票", "30分钟达"), "美食", "张江商圈", "川", 31, 1.8, 5832, "出餐快，近 7 日准时率 98%", "准时宝", List.of("满60减8", "满90减15", "会员再减3"), List.of("5元无门槛", "配送费券"), 98));
        stores.put(102L, new Store(102, merchant102.id, "轻食研究所", "高峰期预计 45 分钟送达", true, money("25"), money("5"), 4.7, List.of("轻食", "低脂", "沙拉", "品质联盟"), "美食", "世纪公园", "轻", 38, 2.4, 3189, "冷链备餐，骑手保温箱配送", "坏单包退", List.of("满49减6", "满79减12"), List.of("新客立减8"), 88));
        stores.put(103L, new Store(103, merchant103.id, "阿婆面馆", "招牌红烧牛肉面，现点现煮", true, money("18"), money("3"), 4.9, List.of("面馆", "老字号", "月售999+"), "美食", "陆家嘴", "面", 26, 1.2, 9241, "堂食外卖双线高峰，建议提前下单", "慢必赔", List.of("满35减5", "第二份半价"), List.of("收藏领2元券"), 95));
        stores.put(104L, new Store(104, merchant104.id, "熊猫茶事", "奶茶咖啡 24 分钟送达", true, money("15"), money("2"), 4.6, List.of("奶茶", "咖啡", "下午茶"), "甜点饮品", "张江商圈", "茶", 24, 0.9, 7120, "爆品排队中，冰量糖度可备注", "撒漏包赔", List.of("满30减4", "买二赠一"), List.of("饮品券包"), 91));
        stores.put(105L, new Store(105, merchant105.id, "便利蜂速达", "零食饮料、纸品日化、应急药箱", true, money("10"), money("4"), 4.5, List.of("超市", "便利", "24小时"), "超市便利", "金科路", "便", 22, 1.1, 11023, "夜间订单由专职骑手配送", "缺货秒退", List.of("满59减10", "满99减18"), List.of("新人 9.9 包邮"), 93));
        stores.put(106L, new Store(106, merchant106.id, "鲜果切仓", "现切水果、果篮和轻加工生鲜", true, money("29"), money("5"), 4.8, List.of("水果", "生鲜", "次日达"), "水果买菜", "碧云社区", "果", 42, 3.3, 2876, "门店质检后出库，坏果可售后", "坏果包退", List.of("满45减7", "满88减16"), List.of("果切券"), 82));
        stores.put(107L, new Store(107, merchant107.id, "深夜烧烤局", "22:00 后夜宵爆单，配送费限时减免", true, money("35"), money("6"), 4.4, List.of("烧烤", "夜宵", "啤酒"), "夜宵", "唐镇", "烤", 48, 4.1, 6428, "晚高峰爆单，系统自动顺路派单", "超时补偿", List.of("满80减12", "满120减25"), List.of("夜宵红包"), 78));
        stores.put(108L, new Store(108, merchant108.id, "城市跑腿站", "文件、鲜花、钥匙、药品帮买帮送", true, money("0"), money("8"), 4.7, List.of("跑腿", "帮买", "专人专送"), "跑腿代购", "全城", "跑", 35, 2.0, 1580, "按任务距离计价，敏感物品需审核", "专人直送", List.of("会员配送费 8 折"), List.of("企业跑腿券"), 81));

        products.put(1001L, new Product(1001, 101, "麻辣香锅套餐", "牛肉、虾滑、时蔬和米饭", money("36.80"), 24, true, 318, "热销套餐", "spicy", 1, "招牌", money("42.00")));
        products.put(1002L, new Product(1002, 101, "鱼香肉丝盖饭", "经典川味，默认微辣", money("22.00"), 40, true, 502, "盖饭", "rice", 2, "月售500+", money("26.00")));
        products.put(1003L, new Product(1003, 101, "红糖冰粉", "红糖桂花冰粉，解辣搭档", money("8.00"), 80, true, 268, "小吃饮品", "dessert", 5, "加购", money("10.00")));
        products.put(1004L, new Product(1004, 101, "水煮牛肉双人餐", "牛肉、豆芽、莴笋和两份米饭", money("68.00"), 18, true, 209, "热销套餐", "spicy", 3, "满减优选", money("78.00")));
        products.put(1005L, new Product(1005, 101, "宫保鸡丁", "鸡腿肉、花生、微甜辣口", money("29.00"), 32, true, 341, "单点热菜", "wok", 4, "下饭菜", money("35.00")));
        products.put(2001L, new Product(2001, 102, "鸡胸肉能量碗", "糙米、鸡胸肉、牛油果和蔬菜", money("32.00"), 20, true, 126, "能量碗", "fresh", 1, "低脂", money("39.00")));
        products.put(2002L, new Product(2002, 102, "鲜榨橙汁", "不额外加糖", money("12.00"), 16, true, 88, "饮品", "juice", 3, "现榨", money("16.00")));
        products.put(2003L, new Product(2003, 102, "三文鱼沙拉", "三文鱼、藜麦、鸡蛋和芝麻菜", money("46.00"), 14, true, 96, "沙拉", "fresh", 2, "高蛋白", money("52.00")));
        products.put(3001L, new Product(3001, 103, "红烧牛肉面", "大块牛腩，手工面条", money("28.00"), 60, true, 1280, "招牌面", "noodle", 1, "榜单第1", money("35.00")));
        products.put(3002L, new Product(3002, 103, "葱油拌面", "熬制葱油，配溏心蛋", money("19.00"), 70, true, 802, "拌面", "noodle", 2, "人气", money("22.00")));
        products.put(3003L, new Product(3003, 103, "炸猪排", "现炸猪排，可搭配面食", money("16.00"), 44, true, 421, "小吃", "snack", 3, "加购", money("19.00")));
        products.put(4001L, new Product(4001, 104, "熊猫珍珠奶茶", "默认三分糖，黑糖珍珠", money("18.00"), 90, true, 1770, "奶茶", "drink", 1, "爆款", money("22.00")));
        products.put(4002L, new Product(4002, 104, "生椰拿铁", "厚椰乳与冷萃咖啡", money("21.00"), 72, true, 930, "咖啡", "coffee", 2, "第二杯半价", money("25.00")));
        products.put(4003L, new Product(4003, 104, "芋泥蛋糕卷", "冷藏配送，口感绵密", money("16.00"), 38, true, 226, "甜点", "cake", 3, "下午茶", money("19.00")));
        products.put(5001L, new Product(5001, 105, "可乐 6 听装", "330ml 经典可乐", money("18.90"), 120, true, 1502, "酒水饮料", "market", 1, "家庭装", money("22.80")));
        products.put(5002L, new Product(5002, 105, "抽纸 3 包", "原生木浆，家庭常备", money("15.90"), 88, true, 760, "日用百货", "market", 2, "应急", money("19.90")));
        products.put(5003L, new Product(5003, 105, "关东煮组合", "鱼丸、萝卜、豆腐串", money("16.80"), 36, true, 421, "即食热餐", "market", 3, "热柜", money("20.00")));
        products.put(6001L, new Product(6001, 106, "现切西瓜盒", "冷藏现切，约 500g", money("18.80"), 35, true, 620, "现切果盒", "fruit", 1, "坏果包退", money("22.00")));
        products.put(6002L, new Product(6002, 106, "阳光玫瑰 500g", "当日质检装盒", money("39.90"), 28, true, 320, "精品水果", "fruit", 2, "高甜", money("49.90")));
        products.put(7001L, new Product(7001, 107, "羊肉串 10 串", "肥瘦相间，默认中辣", money("48.00"), 42, true, 960, "烧烤", "bbq", 1, "夜宵爆款", money("58.00")));
        products.put(7002L, new Product(7002, 107, "烤苕皮", "重庆风味，外脆内糯", money("9.90"), 80, true, 740, "小串", "bbq", 2, "加购", money("12.00")));
        products.put(8001L, new Product(8001, 108, "帮买药品", "按小票实付结算，需备注药店", money("8.00"), 999, true, 190, "跑腿服务", "errand", 1, "专人", money("10.00")));
        products.put(8002L, new Product(8002, 108, "文件同城送", "专人直送，签收回传", money("18.00"), 999, true, 168, "跑腿服务", "errand", 2, "加急", money("25.00")));

        seedVirtualStores();

        carts.put(customer.id, new ArrayList<>(List.of(
                new CartItem(ids.incrementAndGet(), 1001, 101, "麻辣香锅套餐", money("36.80"), 1),
                new CartItem(ids.incrementAndGet(), 1003, 101, "冰粉", money("8.00"), 1)
        )));
    }

    private void seedVirtualStores() {
        String[] categories = {"美食", "甜点饮品", "超市便利", "水果买菜", "夜宵", "品牌连锁", "品质联盟", "跑腿代购"};
        String[] areas = {"张江", "陆家嘴", "徐家汇", "五角场", "静安寺", "虹桥", "世纪公园", "唐镇"};
        String[] names = {"城市小馆", "鲜食工坊", "云上厨房", "好味档口", "邻里食集", "即刻补给", "优选小站", "暖心餐室"};
        for (int i = 0; i < 160; i++) {
            long storeId = 10_000L + i;
            String category = categories[i % categories.length];
            String area = areas[i % areas.length];
            String name = area + names[i % names.length] + (i + 1);
            Account owner = new Account(30_000L + i, "merchant_" + areaSlug(area) + "_" + storeId, passwordHash("123456"),
                    name + "商家", Role.MERCHANT, storeId, "139" + String.format("%08d", i));
            accounts.put(owner.id, owner);
            int monthlySales = 300 + ((i * 137) % 9800);
            int minutes = 18 + (i % 36);
            double distance = Math.round((0.4 + (i % 45) * 0.17) * 10.0) / 10.0;
            stores.put(storeId, new Store(storeId, owner.id, name, "系统模拟海量门店，支持分页、排序和动态加载", true,
                    money(String.valueOf(12 + (i % 30))), money(String.valueOf(2 + (i % 7))),
                    4.1 + ((i % 9) * 0.1), List.of(category, area, "平台优选"), category, area, name.substring(0, 1),
                    minutes, distance, monthlySales, "近 7 日履约稳定，支持高峰期调度", "准时宝",
                    List.of("满" + (35 + i % 40) + "减" + (4 + i % 9), "会员红包可用"), List.of("平台券", "配送券"),
                    60 + (i % 40)));
            for (int j = 0; j < 3; j++) {
                long productId = 90_000L + i * 10L + j;
                products.put(productId, new Product(productId, storeId,
                        category + "热卖" + (j + 1), name + " 爆款商品 " + (j + 1),
                        money(String.valueOf(9 + ((i + j) % 60)) + ".90"),
                        80 + ((i + j) % 120), true, 50 + ((i + j) * 29 % 1500),
                        j == 0 ? "招牌" : "热销", "default", j + 1, "海量样本", money(String.valueOf(15 + ((i + j) % 65)) + ".90")));
            }
        }
    }

    private String areaSlug(String area) {
        return switch (area) {
            case "张江" -> "zhangjiang";
            case "陆家嘴" -> "lujiazui";
            case "徐家汇" -> "xujiahui";
            case "五角场" -> "wujiaochang";
            case "静安寺" -> "jingansi";
            case "虹桥" -> "hongqiao";
            case "世纪公园" -> "shijigongyuan";
            case "唐镇" -> "tangzhen";
            default -> "store";
        };
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private PageWindow pageWindow(int total, int page, int size, int defaultSize) {
        int pageValue = Math.max(0, page);
        int sizeValue = Math.max(5, Math.min(30, size <= 0 ? defaultSize : size));
        int from = Math.min(pageValue * sizeValue, total);
        int to = Math.min(from + sizeValue, total);
        return new PageWindow(pageValue, sizeValue, from, to);
    }

    private static String passwordHash(String rawPassword) {
        if ("123456".equals(rawPassword)) {
            return DEMO_PASSWORD_HASH;
        }
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    private static boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return PASSWORD_ENCODER.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private AuthSession issueToken(Account account, Role portal) {
        String token = "tk-" + account.role.name().toLowerCase() + "-" + UUID.randomUUID();
        AuthSession session = new AuthSession(token, account.id, portal, LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        tokens.put(token, session);
        return session;
    }

    private AuthSession activeSession(String token) {
        if (token == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        AuthSession session = tokens.get(token);
        if (session == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (session.expiresAt.isBefore(LocalDateTime.now())) {
            tokens.remove(token);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return session;
    }

    private SessionView sessionView(AuthSession session, Account account) {
        return new SessionView(session.token, account.id, account.username, account.displayName,
                account.role, session.portal, account.storeId, session.expiresAt);
    }

    private Role requirePortal(String portal) {
        String value = requireText(portal, "登录端不能为空").toUpperCase();
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "登录端不合法");
        }
    }

    private String parseToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private Product requireProduct(Long productId) {
        Product product = productId == null ? null : products.get(productId);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private Store requireStore(Long storeId) {
        Store store = storeId == null ? null : stores.get(storeId);
        if (store == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "门店不存在");
        }
        return store;
    }

    private Address requireAddress(Account account, Long addressId) {
        Address address = addressId == null ? null : addresses.get(addressId);
        if (address == null || address.userId != account.id) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "收货地址不存在或无权使用");
        }
        return address;
    }

    private Order requireOrder(long orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private Ticket requireTicket(long ticketId) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后工单不存在");
        }
        return ticket;
    }

    private Review requireReview(long reviewId) {
        Review review = reviews.get(reviewId);
        if (review == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "评价不存在");
        }
        return review;
    }

    private OnboardingApplication requireOnboardingApplication(long applicationId) {
        OnboardingApplication application = onboardingApplications.get(applicationId);
        if (application == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入驻申请不存在");
        }
        return application;
    }

    private DeliveryOrder requireRiderTask(Account rider, long taskId) {
        DeliveryOrder delivery = deliveries.get(taskId);
        if (delivery == null || delivery.riderId == null || delivery.riderId != rider.id) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "配送任务不存在或未分配给当前骑手");
        }
        return delivery;
    }

    private long requireMerchantStoreId(Account merchant) {
        if (merchant.storeId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前商家未绑定门店");
        }
        return merchant.storeId;
    }

    private Order requireMerchantOrder(Account merchant, long orderId) {
        Order order = requireOrder(orderId);
        if (!merchantOwnsStore(merchant, order.storeId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能处理本门店订单");
        }
        return order;
    }

    private void ensureMerchantProduct(Account merchant, Product product) {
        if (!merchantOwnsStore(merchant, product.storeId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能维护本门店商品");
        }
    }

    private boolean merchantOwnsStore(Account merchant, long storeId) {
        Store store = stores.get(storeId);
        return store != null && store.merchantId == merchant.id;
    }

    private List<CreateOrderItem> normalizeOrderItems(Account account, CreateOrderRequest request) {
        if (request.items != null && !request.items.isEmpty()) {
            return request.items;
        }
        return cartItems(account).stream()
                .map(item -> new CreateOrderItem(item.productId, item.quantity))
                .toList();
    }

    private void refreshCartItem(CartItem item) {
        Product product = requireProduct(item.productId);
        item.storeId = product.storeId;
        item.productName = product.name;
        item.price = product.price;
        item.available = product.available();
        item.subtotal = product.price.multiply(BigDecimal.valueOf(item.quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal discount(BigDecimal itemAmount, UserCoupon coupon) {
        BigDecimal discount = ZERO;
        if (itemAmount.compareTo(money("60")) >= 0) {
            discount = discount.add(money("8"));
        }
        if (coupon != null) {
            discount = discount.add(coupon.discountAmount);
        }
        return discount.min(itemAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCouponCode(String couponCode) {
        return Objects.toString(couponCode, "").trim().toUpperCase();
    }

    private UserCoupon resolveUsableCoupon(Account account, BigDecimal itemAmount, String couponCode) {
        String normalizedCode = normalizeCouponCode(couponCode);
        if (normalizedCode.isBlank()) {
            return null;
        }
        UserCoupon coupon = userCouponInstances.values().stream()
                .filter(item -> item.userId == account.id)
                .filter(item -> item.couponCode.equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "优惠券不存在或不属于当前用户"));
        refreshCouponStatus(coupon);
        if (!"UNUSED".equals(coupon.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "优惠券当前状态为 " + coupon.status + "，不可使用");
        }
        if (itemAmount.compareTo(coupon.thresholdAmount) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "未达到优惠券门槛 " + coupon.thresholdAmount + " 元");
        }
        return coupon;
    }

    private void lockCouponHold(Account account, Order order) {
        if (order.couponCode == null || order.couponCode.isBlank()) {
            return;
        }
        UserCoupon coupon = requireUserCoupon(account.id, order.couponCode);
        refreshCouponStatus(coupon);
        if (!"UNUSED".equals(coupon.status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "优惠券当前状态为 " + coupon.status + "，不可锁定");
        }
        String before = coupon.status;
        coupon.status = "LOCKED";
        coupon.lockedOrderId = order.id;
        coupon.reason = "已锁定到待支付订单 " + order.orderNo;
        coupon.updatedAt = LocalDateTime.now();
        couponOrderLocks.put(couponLockKey(account.id, order.couponCode), order.id);
        appendCouponLog(coupon, before, coupon.status, account.displayName, coupon.reason, order.id);
        outbox("COUPON_HELD", "COUPON", account.id, order.couponCode + " 已锁定到订单 " + order.orderNo);
    }

    private void consumeCouponHold(Order order) {
        if (order.couponCode == null || order.couponCode.isBlank()) {
            return;
        }
        UserCoupon coupon = requireUserCoupon(order.customerId, order.couponCode);
        String before = coupon.status;
        coupon.status = "USED";
        coupon.usedOrderId = order.id;
        coupon.lockedOrderId = null;
        coupon.reason = "已随订单 " + order.orderNo + " 支付核销";
        coupon.updatedAt = LocalDateTime.now();
        couponOrderLocks.remove(couponLockKey(order.customerId, order.couponCode), order.id);
        appendCouponLog(coupon, before, coupon.status, "PAYMENT_CALLBACK", coupon.reason, order.id);
        outbox("COUPON_CONSUMED", "COUPON", order.customerId, order.couponCode + " 已随订单 " + order.orderNo + " 支付核销");
    }

    private void releaseCouponHold(Order order) {
        if (order.couponCode == null || order.couponCode.isBlank()) {
            return;
        }
        UserCoupon coupon = requireUserCoupon(order.customerId, order.couponCode);
        if ("EXPIRED".equals(coupon.status)) {
            return;
        }
        String before = coupon.status;
        coupon.status = coupon.validTo.isBefore(LocalDateTime.now()) ? "EXPIRED" : "UNUSED";
        coupon.lockedOrderId = null;
        coupon.usedOrderId = null;
        coupon.reason = "已从订单 " + order.orderNo + " 释放";
        coupon.updatedAt = LocalDateTime.now();
        couponOrderLocks.remove(couponLockKey(order.customerId, order.couponCode), order.id);
        appendCouponLog(coupon, before, coupon.status, "ORDER_COMPENSATION", coupon.reason, order.id);
        if (!before.equals(coupon.status)) {
            outbox("COUPON_RELEASED", "COUPON", order.customerId, order.couponCode + " 已从订单 " + order.orderNo + " 释放");
        }
    }

    private String couponLockKey(long userId, String couponCode) {
        return userId + ":" + normalizeCouponCode(couponCode);
    }

    private String normalizeCouponScene(String sceneCode) {
        String scene = Objects.toString(sceneCode, "").trim().toUpperCase();
        if (!couponActivities.containsKey(scene)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        return scene;
    }

    private CouponActivity currentCouponActivity(String scene) {
        CouponActivity activity = couponActivities.get(scene);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || now.isBefore(activity.startAt) || !now.isBefore(activity.endAt)) {
            throw new BusinessException(HttpStatus.CONFLICT, "活动不在可领取时间内");
        }
        return activity;
    }

    private long remainingCouponStock(CouponActivity activity) {
        return redisEnabled()
                ? redisFeatureStore.remainingCouponStock(activity.batchCode)
                : memoryCouponStocks.getOrDefault(activity.batchCode, 0);
    }

    private List<UserCoupon> issueCouponPack(Account account, CouponActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> issued = new ArrayList<>();
        for (CouponTemplate template : activity.templates) {
            long couponId = ids.incrementAndGet();
            UserCoupon coupon = new UserCoupon(
                    couponId,
                    activity.sceneCode.substring(0, Math.min(1, activity.sceneCode.length()))
                            + now.toLocalDate().toString().replace("-", "")
                            + "-" + couponId,
                    account.id,
                    activity.sceneCode,
                    activity.batchCode,
                    template.templateCode,
                    template.title,
                    template.discountAmount,
                    template.thresholdAmount,
                    template.scope,
                    "UNUSED",
                    null,
                    null,
                    "可在结算页选择使用",
                    now,
                    now,
                    activity.endAt,
                    now);
            userCouponInstances.put(coupon.id, coupon);
            appendCouponLog(coupon, null, coupon.status, account.displayName, "用户领取 " + activity.batchCode + " 券包", null);
            issued.add(coupon);
        }
        return issued;
    }

    private UserCoupon requireUserCoupon(long userId, String couponCode) {
        String normalizedCode = normalizeCouponCode(couponCode);
        return userCouponInstances.values().stream()
                .filter(coupon -> coupon.userId == userId)
                .filter(coupon -> coupon.couponCode.equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "优惠券不存在"));
    }

    private void refreshCouponStatus(UserCoupon coupon) {
        if (("UNUSED".equals(coupon.status) || "LOCKED".equals(coupon.status))
                && coupon.validTo.isBefore(LocalDateTime.now())) {
            String before = coupon.status;
            Long relatedOrderId = coupon.lockedOrderId;
            coupon.status = "EXPIRED";
            coupon.lockedOrderId = null;
            coupon.reason = "已超过有效期";
            coupon.updatedAt = LocalDateTime.now();
            if (relatedOrderId != null) {
                couponOrderLocks.remove(couponLockKey(coupon.userId, coupon.couponCode), relatedOrderId);
            }
            appendCouponLog(coupon, before, coupon.status, "SYSTEM", coupon.reason, relatedOrderId);
        }
    }

    private void appendCouponLog(UserCoupon coupon, String before, String after, String operator, String reason, Long relatedOrderId) {
        CouponStatusLog log = new CouponStatusLog(
                ids.incrementAndGet(),
                coupon.id,
                coupon.couponCode,
                coupon.userId,
                before,
                after,
                relatedOrderId,
                operator,
                reason,
                LocalDateTime.now());
        couponStatusLogs.put(log.id, log);
        persistUserCoupon(coupon);
        persistCouponStatusLog(log);
    }

    private boolean allowRate(String key, int limit, int windowMs) {
        if (redisEnabled()) {
            return redisFeatureStore.allowSlidingWindow(key, limit, windowMs);
        }
        long now = System.currentTimeMillis();
        List<Long> records = memoryRateWindows.computeIfAbsent(key, ignored -> new ArrayList<>());
        records.removeIf(time -> time <= now - windowMs);
        if (records.size() >= limit) {
            return false;
        }
        records.add(now);
        return true;
    }

    private boolean captchaPassed(CouponClaimRequest request) {
        return request != null && "8".equals(Objects.toString(request.captchaAnswer, "").trim());
    }

    private CouponWalletView couponWalletView(UserCoupon coupon) {
        Order relatedOrder = coupon.lockedOrderId == null
                ? coupon.usedOrderId == null ? null : orders.get(coupon.usedOrderId)
                : orders.get(coupon.lockedOrderId);
        return new CouponWalletView(
                coupon.sceneCode,
                coupon.batchCode,
                coupon.couponCode,
                coupon.title,
                "减 " + coupon.discountAmount.stripTrailingZeros().toPlainString() + " 元",
                "满 " + coupon.thresholdAmount.stripTrailingZeros().toPlainString() + " 可用",
                coupon.scope,
                coupon.status,
                coupon.reason,
                relatedOrder == null ? null : relatedOrder.id,
                relatedOrder == null ? null : relatedOrder.orderNo,
                coupon.claimedAt,
                coupon.validFrom,
                coupon.validTo,
                couponStatusLogViews(coupon.id));
    }

    private CouponActivityView couponActivityView(CouponActivity activity) {
        long claimedPacks = userCouponInstances.values().stream()
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .map(coupon -> coupon.userId + ":" + coupon.batchCode)
                .distinct()
                .count();
        long issuedCoupons = userCouponInstances.values().stream()
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .count();
        long lockedCoupons = userCouponInstances.values().stream()
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .filter(coupon -> "LOCKED".equals(coupon.status))
                .count();
        long usedCoupons = userCouponInstances.values().stream()
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .filter(coupon -> "USED".equals(coupon.status))
                .count();
        long expiredCoupons = userCouponInstances.values().stream()
                .filter(coupon -> coupon.batchCode.equals(activity.batchCode))
                .filter(coupon -> "EXPIRED".equals(coupon.status))
                .count();
        return new CouponActivityView(
                activity.sceneCode,
                activity.batchCode,
                activity.title,
                activity.subtitle,
                activity.startAt,
                activity.endAt,
                activity.perUserLimit,
                activity.stock,
                remainingCouponStock(activity),
                claimedPacks,
                issuedCoupons,
                lockedCoupons,
                usedCoupons,
                expiredCoupons);
    }

    private AdminCouponView adminCouponView(UserCoupon coupon) {
        Order relatedOrder = coupon.lockedOrderId == null
                ? coupon.usedOrderId == null ? null : orders.get(coupon.usedOrderId)
                : orders.get(coupon.lockedOrderId);
        Account owner = accounts.get(coupon.userId);
        return new AdminCouponView(
                coupon.id,
                coupon.couponCode,
                coupon.userId,
                owner == null ? "未知用户" : owner.displayName,
                owner == null ? "" : maskPhone(owner.phone),
                coupon.sceneCode,
                coupon.batchCode,
                coupon.templateCode,
                coupon.title,
                "减 " + coupon.discountAmount.stripTrailingZeros().toPlainString() + " 元",
                "满 " + coupon.thresholdAmount.stripTrailingZeros().toPlainString() + " 可用",
                coupon.scope,
                coupon.status,
                coupon.reason,
                relatedOrder == null ? null : relatedOrder.id,
                relatedOrder == null ? null : relatedOrder.orderNo,
                coupon.claimedAt,
                coupon.validFrom,
                coupon.validTo,
                coupon.updatedAt);
    }

    private boolean couponMatchesKeyword(UserCoupon coupon, String keyword) {
        Account owner = accounts.get(coupon.userId);
        Order relatedOrder = coupon.lockedOrderId == null
                ? coupon.usedOrderId == null ? null : orders.get(coupon.usedOrderId)
                : orders.get(coupon.lockedOrderId);
        return List.of(
                        coupon.couponCode,
                        coupon.sceneCode,
                        coupon.batchCode,
                        coupon.templateCode,
                        coupon.title,
                        coupon.scope,
                        coupon.status,
                        coupon.reason,
                        owner == null ? "" : owner.displayName,
                        owner == null ? "" : owner.username,
                        owner == null ? "" : owner.phone,
                        relatedOrder == null ? "" : relatedOrder.orderNo
                ).stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(keyword));
    }

    private List<CouponStatusLogView> couponStatusLogViews(long couponId) {
        return couponStatusLogs.values().stream()
                .filter(log -> log.couponId == couponId)
                .sorted(Comparator.comparing((CouponStatusLog log) -> log.createdAt).reversed())
                .map(this::couponStatusLogView)
                .toList();
    }

    private CouponStatusLogView couponStatusLogView(CouponStatusLog log) {
        Order relatedOrder = log.relatedOrderId == null ? null : orders.get(log.relatedOrderId);
        Account owner = accounts.get(log.userId);
        return new CouponStatusLogView(
                log.id,
                log.couponId,
                log.couponCode,
                log.userId,
                owner == null ? "未知用户" : owner.displayName,
                log.beforeStatus,
                log.afterStatus,
                log.relatedOrderId,
                relatedOrder == null ? null : relatedOrder.orderNo,
                log.operatorName,
                log.reason,
                log.createdAt);
    }

    private ReviewView reviewView(Review review) {
        Order order = orders.get(review.orderId);
        return new ReviewView(
                review.id,
                review.orderId,
                order == null ? "" : order.orderNo,
                order == null ? 0 : order.storeId,
                order == null ? "" : order.storeName,
                review.customerId,
                accounts.getOrDefault(review.customerId, new Account(0, "", "", "用户", Role.CUSTOMER, null, "")).displayName,
                review.score,
                review.content,
                review.merchantReply,
                review.repliedAt,
                review.createdAt);
    }

    private TicketView ticketView(Ticket ticket) {
        Order order = orders.get(ticket.orderId);
        return new TicketView(ticket.id, ticket.orderId, order == null ? "" : order.orderNo,
                order == null ? "" : order.storeName, ticket.status, ticket.reason, ticket.result,
                ticket.createdAt, ticket.finishedAt);
    }

    private OnboardingApplicationView applicationView(OnboardingApplication application) {
        Account account = application.createdAccountId == null ? null : accounts.get(application.createdAccountId);
        Store store = application.createdStoreId == null ? null : stores.get(application.createdStoreId);
        return new OnboardingApplicationView(
                application.id,
                application.role,
                application.status,
                application.applicantName,
                maskPhone(application.phone),
                application.storeName,
                application.category,
                application.address,
                application.preferredUsername,
                application.reason,
                application.result,
                application.createdAccountId,
                account == null ? null : account.username,
                application.createdStoreId,
                store == null ? null : store.name,
                application.createdAt,
                application.finishedAt);
    }

    private void reserveStock(List<OrderItem> orderItems) {
        boolean redisReserved = false;
        if (redisEnabled()) {
            redisReserved = redisFeatureStore.reserveStockBatch(orderItems);
            if (!redisReserved) {
                throw new BusinessException(HttpStatus.CONFLICT, "库存不足，请刷新后重试");
            }
        }
        if (databaseEnabled()) {
            boolean reserved = databaseStore.reserveStockBatch(orderItems);
            if (!reserved) {
                if (redisReserved) {
                    orderItems.forEach(item -> redisFeatureStore.releaseStock(item.productId, item.quantity));
                }
                throw new BusinessException(HttpStatus.CONFLICT, "库存不足，请刷新后重试");
            }
        }
        for (OrderItem item : orderItems) {
            decreaseMemoryStock(item);
        }
    }

    private void decreaseMemoryStock(OrderItem item) {
        Product product = products.get(item.productId);
        if (product != null) {
            product.stock -= item.quantity;
        }
    }

    private PaymentOrder paymentByOrder(long orderId) {
        return payments.values().stream()
                .filter(payment -> payment.orderId == orderId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "支付单不存在"));
    }

    private RefundOrder refundByOrder(long orderId) {
        return refunds.values().stream()
                .filter(refund -> refund.orderId == orderId && !"SUCCESS".equals(refund.status))
                .findFirst()
                .orElseGet(() -> refunds.values().stream()
                        .filter(refund -> refund.orderId == orderId)
                        .findFirst()
                        .orElse(null));
    }

    private DeliveryOrder deliveryByOrder(long orderId) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.orderId == orderId)
                .findFirst()
                .orElse(null);
    }

    private RefundOrder createRefund(Order order, String reason, String status) {
        RefundOrder refund = new RefundOrder(ids.incrementAndGet(), "RF" + order.id + "-" + ids.get(), order.id, status, order.payAmount, reason, LocalDateTime.now(), null);
        if ("SUCCESS".equals(status)) {
            refund.finishedAt = LocalDateTime.now();
        }
        refunds.put(refund.id, refund);
        order.refundId = refund.id;
        persistRefund(refund);
        return refund;
    }

    private void transition(Order order, OrderStatus target, String operator, String reason) {
        OrderStatus before = order.status;
        OrderStateMachine.assertCanTransit(before, target);
        order.status = target;
        if (target == OrderStatus.COMPLETED || target == OrderStatus.REFUNDED || target == OrderStatus.CANCELLED) {
            order.finishedAt = LocalDateTime.now();
        }
        appendStatus(order, before, target, operator, reason);
        persistOrderAggregate(order);
    }

    private void appendStatus(Order order, OrderStatus before, OrderStatus after, String operator, String reason) {
        LocalDateTime now = LocalDateTime.now();
        order.statusRecords.add(new OrderStatusRecord(ids.incrementAndGet(), order.id, before, after, operator, reason, now));
        order.fulfillmentSteps.add(new FulfillmentStep(ids.incrementAndGet(), order.id, statusLabel(after), after.name(), operator, reason, now));
    }

    private void appendDeliveryLog(DeliveryOrder delivery, String before, String after, String operator, String detail, String location) {
        DeliveryStatusLog log = new DeliveryStatusLog(
                ids.incrementAndGet(),
                delivery.id,
                delivery.orderId,
                delivery.riderId,
                delivery.riderName,
                before,
                after,
                operator,
                detail,
                location,
                LocalDateTime.now());
        delivery.statusLogs.add(log);
        deliveryStatusLogs.put(log.id, log);
        persistDeliveryStatusLog(log);
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case WAIT_PAY -> "订单已提交";
            case PAID_WAIT_ACCEPT -> "支付成功，等待商家接单";
            case PREPARING -> "商家已接单，正在备餐";
            case WAIT_PICKUP -> "商家已出餐，等待骑手取餐";
            case DELIVERING -> "骑手已取餐，正在配送";
            case DELIVERED -> "订单已送达";
            case COMPLETED -> "订单已完成";
            case CANCELLED -> "订单已取消";
            case REFUNDING -> "退款处理中";
            case REFUNDED -> "退款完成";
            case AFTERSALE -> "售后处理中";
        };
    }

    private void releaseReservations(long orderId) {
        reservations.values().stream()
                .filter(reservation -> reservation.orderId == orderId)
                .filter(reservation -> "HELD".equals(reservation.status))
                .forEach(reservation -> {
                    Product product = products.get(reservation.productId);
                    if (product != null) {
                        product.stock += reservation.quantity;
                        if (redisEnabled()) {
                            redisFeatureStore.releaseStock(reservation.productId, reservation.quantity);
                        }
                        if (databaseEnabled()) {
                            databaseStore.releaseStock(reservation.productId, reservation.quantity);
                        } else {
                            persistProduct(product);
                        }
                    }
                    reservation.status = "RELEASED";
                    persistReservation(reservation);
                });
    }

    private void consumeReservations(long orderId) {
        reservations.values().stream()
                .filter(reservation -> reservation.orderId == orderId)
                .filter(reservation -> "HELD".equals(reservation.status))
                .forEach(reservation -> {
                    reservation.status = "CONSUMED";
                    persistReservation(reservation);
                });
    }

    private void outbox(String eventType, String aggregateType, long aggregateId, String payload) {
        OutboxEvent event = newOutboxEvent(eventType, aggregateType, aggregateId, payload);
        outboxEvents.put(event.id, event);
        persistOutbox(event);
    }

    private void audit(Account actor, String action, String objectType, long objectId, String before, String after, String reason) {
        AuditLog log = newAuditLog(actor, action, objectType, objectId, before, after, reason);
        auditLogs.put(log.id, log);
        persistAudit(log);
    }

    private OutboxEvent newOutboxEvent(String eventType, String aggregateType, long aggregateId, String payload) {
        return new OutboxEvent(ids.incrementAndGet(), eventType, aggregateType, aggregateId, payload, "PENDING", 0, LocalDateTime.now());
    }

    private AuditLog newAuditLog(Account actor, String action, String objectType, long objectId, String before, String after, String reason) {
        return new AuditLog(ids.incrementAndGet(), actor.id, actor.displayName, actor.role, action, objectType, objectId, before, after, reason, LocalDateTime.now());
    }

    private Account systemActor() {
        return accounts.values().stream()
                .filter(account -> account.role == Role.ADMIN)
                .findFirst()
                .orElse(new Account(0, "system", "", "系统任务", Role.ADMIN, null, ""));
    }

    private void makeDefaultAddress(Account account, long addressId) {
        for (Address current : addresses.values()) {
            if (current.userId == account.id) {
                current.defaultAddress = current.id == addressId;
                persistAddress(current);
            }
        }
    }

    private String requireText(String value, String message) {
        String text = Objects.toString(value, "").trim();
        if (text.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
        return text;
    }

    private String requireUsername(String username) {
        String value = requireText(username, "账号不能为空").toLowerCase();
        if (!value.matches("[a-z0-9_]{4,40}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "账号只能包含小写字母、数字和下划线，长度 4-40");
        }
        return value;
    }

    private String requirePassword(String password) {
        String value = requireText(password, "密码不能为空");
        if (value.length() < 6) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码长度不能少于 6 位");
        }
        return value;
    }

    private String requirePhone(String phone) {
        String value = requireText(phone, "手机号不能为空");
        if (!value.matches("1\\d{10}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        return value;
    }

    private void ensureUsernameAvailable(String username) {
        if (usernameExists(username)) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号已存在");
        }
    }

    private boolean usernameExists(String username) {
        return accounts.values().stream().anyMatch(account -> account.username.equals(username));
    }

    private void ensurePhoneAvailable(String phone) {
        boolean exists = accounts.values().stream().anyMatch(account -> phone.equals(account.phone));
        if (exists) {
            throw new BusinessException(HttpStatus.CONFLICT, "手机号已绑定账号");
        }
    }

    private void ensureNoPendingApplication(Role role, String phone) {
        boolean exists = onboardingApplications.values().stream()
                .anyMatch(application -> application.role == role
                        && application.phone.equals(phone)
                        && "PENDING".equals(application.status));
        if (exists) {
            throw new BusinessException(HttpStatus.CONFLICT, "该手机号已有待审核入驻申请");
        }
    }

    private String uniqueUsername(String base) {
        String candidate = requireUsername(base);
        int suffix = 1;
        while (usernameExists(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private String defaultText(String value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isBlank() ? fallback : text;
    }

    private double normalizeDistance(Double distanceKm) {
        if (distanceKm == null) {
            return 2.0;
        }
        return Math.max(0.1, Math.min(50.0, Math.round(distanceKm * 10.0) / 10.0));
    }

    private String maskPhoneInput(String phone) {
        return maskPhone(requireText(phone, "手机号不能为空"));
    }

    private List<String> normalizeStringList(List<String> values, int maxSize) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(maxSize)
                .toList();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static class LoginRequest {
        public String username;
        public String password;
        public String portal;
    }

    public static class CustomerRegisterRequest {
        public String username;
        public String password;
        public String displayName;
        public String phone;
    }

    public static class OnboardingApplicationRequest {
        public String applicantName;
        public String phone;
        public String storeName;
        public String category;
        public String address;
        public String preferredUsername;
        public String reason;
    }

    public static class OnboardingApprovalRequest {
        public String username;
        public String initialPassword;
        public String result;
    }

    public static class AddCartItemRequest {
        public long productId;
        public int quantity = 1;
    }

    public static class AddressRequest {
        public String receiver;
        public String phone;
        public String detail;
        public Double distanceKm;
        public Boolean inRange;
        public Boolean defaultAddress;
    }

    public static class CreateOrderRequest {
        public Long storeId;
        public Long addressId;
        public String idempotencyToken;
        public String couponCode;
        public List<CreateOrderItem> items = new ArrayList<>();
    }

    public static class CouponClaimRequest {
        public String captchaAnswer;
    }

    public static class CreateOrderItem {
        public long productId;
        public int quantity;

        public CreateOrderItem() {
        }

        public CreateOrderItem(long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class PayRequest {
        public String callbackFlowNo;
    }

    public static class RefundRequest {
        public String reason;
    }

    public static class ReviewRequest {
        public int score;
        public String content;
    }

    public static class ReviewReplyRequest {
        public String reply;
    }

    public static class MerchantProductRequest {
        public Long id;
        public String name;
        public String description;
        public String price;
        public int stock;
        public boolean onSale = true;
        public String category;
        public String imageTone;
        public String discountLabel;
        public String originalPrice;
    }

    public static class StoreConfigRequest {
        public String name;
        public String notice;
        public Boolean open;
        public String minDeliveryAmount;
        public String deliveryFee;
        public Integer avgDeliveryMinutes;
        public Double deliveryRangeKm;
        public String businessHours;
        public String deliveryGuarantee;
        public String statusMessage;
        public List<String> tags;
        public List<String> promotions;
        public List<String> couponHints;
    }

    public static class RiderExceptionRequest {
        public String type;
        public String detail;
        public String evidenceNo;
        public String reason;
    }

    public static class AdminTicketRequest {
        public String result;
    }

    public record SessionView(String token, long userId, String username, String displayName,
                              Role role, Role portal, Long storeId, LocalDateTime expiresAt) {
    }

    private record AuthSession(String token, long accountId, Role portal, LocalDateTime expiresAt) {
    }

    public record OnboardingApplicationView(long id, Role role, String status, String applicantName, String phoneMasked,
                                            String storeName, String category, String address, String preferredUsername,
                                            String reason, String result, Long createdAccountId, String createdUsername,
                                            Long createdStoreId, String createdStoreName,
                                            LocalDateTime createdAt, LocalDateTime finishedAt) {
    }

    public record UserProfileView(long id, String username, String displayName, String phoneMasked,
                                  int addressCount, long availableCouponCount, int orderCount,
                                  int reviewCount, long pendingAfterSaleCount) {
    }

    public record AccountView(long id, String username, String displayName, Role role, Long storeId) {
    }

    public record MarketHome(List<Channel> channels, List<Banner> banners, List<Store> recommendedStores, List<Product> hotProducts) {
    }

    public record StorePage(List<Store> records, int page, int size, long total, boolean hasMore) {
    }

    public record MerchantAccountPage(List<MerchantAccountView> records, int page, int size, long total, boolean hasMore) {
    }

    public record MerchantAccountView(long id, String username, String displayName, long storeId, String storeName,
                                      String category, String area, int monthlySales, boolean open) {
    }

    public record ProductPage(List<Product> records, int page, int size, long total, boolean hasMore) {
    }

    public record OrderPage(List<Order> records, int page, int size, long total, boolean hasMore) {
    }

    public record AdminCouponPage(List<AdminCouponView> records, int page, int size, long total, boolean hasMore) {
    }

    public record Channel(String name, String subtitle, String code) {
    }

    public record Banner(String title, String subtitle, String actionText, String sceneCode) {
    }

    public record CouponClaimView(String sceneCode, String batchCode, String firstCouponCode, String status,
                                  long remainingStock, int issuedCount, String message) {
    }

    public record CouponActivityView(String sceneCode, String batchCode, String title, String subtitle,
                                     LocalDateTime startAt, LocalDateTime endAt, int perUserLimit, int stock,
                                     long remainingStock, long claimedPacks, long issuedCoupons,
                                     long lockedCoupons, long usedCoupons, long expiredCoupons) {
    }

    public record CouponWalletView(String sceneCode, String batchCode, String couponCode, String title,
                                   String discountText, String thresholdText, String scope, String status,
                                   String reason, Long relatedOrderId, String relatedOrderNo,
                                   LocalDateTime claimedAt, LocalDateTime validFrom, LocalDateTime validTo,
                                   List<CouponStatusLogView> statusLogs) {
    }

    public record AdminCouponView(long id, String couponCode, long userId, String userName, String phoneMasked,
                                  String sceneCode, String batchCode, String templateCode, String title,
                                  String discountText, String thresholdText, String scope, String status,
                                  String reason, Long relatedOrderId, String relatedOrderNo,
                                  LocalDateTime claimedAt, LocalDateTime validFrom, LocalDateTime validTo,
                                  LocalDateTime updatedAt) {
    }

    public record CouponStatusLogView(long id, long couponId, String couponCode, long userId, String userName,
                                      String beforeStatus, String afterStatus, Long relatedOrderId,
                                      String relatedOrderNo, String operatorName, String reason,
                                      LocalDateTime createdAt) {
    }

    public record ReviewView(long id, long orderId, String orderNo, long storeId, String storeName,
                             long customerId, String customerName, int score, String content,
                             String merchantReply, LocalDateTime repliedAt, LocalDateTime createdAt) {
    }

    public record TicketView(long id, long orderId, String orderNo, String storeName, String status,
                             String reason, String result, LocalDateTime createdAt, LocalDateTime finishedAt) {
    }

    private record PageWindow(int page, int size, int from, int to) {
    }

    public static class DemoSnapshot {
        public List<AccountView> accounts;
        public List<Store> stores;
        public List<Product> products;
        public List<Order> orders;
        public List<DeliveryOrder> deliveries;
        public List<DeliveryStatusLog> deliveryStatusLogs;
        public List<InventoryReservation> reservations;
        public List<OutboxEvent> outboxEvents;
        public List<OutboxConsumeRecord> outboxConsumeRecords;
        public List<MerchantNotification> merchantNotifications;
        public List<AuditLog> auditLogs;
    }

    public static class Account {
        public long id;
        public String username;
        public String password;
        public String displayName;
        public Role role;
        public Long storeId;
        public String phone;

        public Account(long id, String username, String password, String displayName, Role role, Long storeId, String phone) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.displayName = displayName;
            this.role = role;
            this.storeId = storeId;
            this.phone = phone;
        }
    }

    public static class OnboardingApplication {
        public long id;
        public Role role;
        public String status;
        public String applicantName;
        public String phone;
        public String storeName;
        public String category;
        public String address;
        public String preferredUsername;
        public String reason;
        public String result;
        public Long createdAccountId;
        public Long createdStoreId;
        public LocalDateTime createdAt;
        public LocalDateTime finishedAt;

        public OnboardingApplication(long id, Role role, String status, String applicantName, String phone,
                                     String storeName, String category, String address, String preferredUsername,
                                     String reason, String result, Long createdAccountId, Long createdStoreId,
                                     LocalDateTime createdAt, LocalDateTime finishedAt) {
            this.id = id;
            this.role = role;
            this.status = status;
            this.applicantName = applicantName;
            this.phone = phone;
            this.storeName = storeName;
            this.category = category;
            this.address = address;
            this.preferredUsername = preferredUsername;
            this.reason = reason;
            this.result = result;
            this.createdAccountId = createdAccountId;
            this.createdStoreId = createdStoreId;
            this.createdAt = createdAt;
            this.finishedAt = finishedAt;
        }
    }

    public static class CouponActivity {
        public String sceneCode;
        public String batchCode;
        public String title;
        public String subtitle;
        public LocalDateTime startAt;
        public LocalDateTime endAt;
        public int perUserLimit;
        public int stock;
        public List<CouponTemplate> templates;

        public CouponActivity(String sceneCode, String batchCode, String title, String subtitle,
                              LocalDateTime startAt, LocalDateTime endAt, int perUserLimit, int stock,
                              List<CouponTemplate> templates) {
            this.sceneCode = sceneCode;
            this.batchCode = batchCode;
            this.title = title;
            this.subtitle = subtitle;
            this.startAt = startAt;
            this.endAt = endAt;
            this.perUserLimit = perUserLimit;
            this.stock = stock;
            this.templates = templates;
        }
    }

    public static class CouponTemplate {
        public String templateCode;
        public String title;
        public BigDecimal discountAmount;
        public BigDecimal thresholdAmount;
        public String scope;

        public CouponTemplate(String templateCode, String title, BigDecimal discountAmount, BigDecimal thresholdAmount, String scope) {
            this.templateCode = templateCode;
            this.title = title;
            this.discountAmount = discountAmount;
            this.thresholdAmount = thresholdAmount;
            this.scope = scope;
        }
    }

    public static class UserCoupon {
        public long id;
        public String couponCode;
        public long userId;
        public String sceneCode;
        public String batchCode;
        public String templateCode;
        public String title;
        public BigDecimal discountAmount;
        public BigDecimal thresholdAmount;
        public String scope;
        public String status;
        public Long lockedOrderId;
        public Long usedOrderId;
        public String reason;
        public LocalDateTime claimedAt;
        public LocalDateTime validFrom;
        public LocalDateTime validTo;
        public LocalDateTime updatedAt;

        public UserCoupon(long id, String couponCode, long userId, String sceneCode, String batchCode,
                          String templateCode, String title, BigDecimal discountAmount, BigDecimal thresholdAmount,
                          String scope, String status, Long lockedOrderId, Long usedOrderId, String reason,
                          LocalDateTime claimedAt, LocalDateTime validFrom, LocalDateTime validTo,
                          LocalDateTime updatedAt) {
            this.id = id;
            this.couponCode = couponCode;
            this.userId = userId;
            this.sceneCode = sceneCode;
            this.batchCode = batchCode;
            this.templateCode = templateCode;
            this.title = title;
            this.discountAmount = discountAmount;
            this.thresholdAmount = thresholdAmount;
            this.scope = scope;
            this.status = status;
            this.lockedOrderId = lockedOrderId;
            this.usedOrderId = usedOrderId;
            this.reason = reason;
            this.claimedAt = claimedAt;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.updatedAt = updatedAt;
        }
    }

    public static class CouponStatusLog {
        public long id;
        public long couponId;
        public String couponCode;
        public long userId;
        public String beforeStatus;
        public String afterStatus;
        public Long relatedOrderId;
        public String operatorName;
        public String reason;
        public LocalDateTime createdAt;

        public CouponStatusLog(long id, long couponId, String couponCode, long userId, String beforeStatus,
                               String afterStatus, Long relatedOrderId, String operatorName, String reason,
                               LocalDateTime createdAt) {
            this.id = id;
            this.couponId = couponId;
            this.couponCode = couponCode;
            this.userId = userId;
            this.beforeStatus = beforeStatus;
            this.afterStatus = afterStatus;
            this.relatedOrderId = relatedOrderId;
            this.operatorName = operatorName;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }

    public static class Address {
        public long id;
        public long userId;
        public String receiver;
        public String phoneMasked;
        public String detailMasked;
        public double distanceKm;
        public boolean inRange;
        public boolean defaultAddress;

        public Address(long id, long userId, String receiver, String phoneMasked, String detailMasked, double distanceKm, boolean inRange, boolean defaultAddress) {
            this.id = id;
            this.userId = userId;
            this.receiver = receiver;
            this.phoneMasked = phoneMasked;
            this.detailMasked = detailMasked;
            this.distanceKm = distanceKm;
            this.inRange = inRange;
            this.defaultAddress = defaultAddress;
        }
    }

    public static class Store {
        public long id;
        public long merchantId;
        public String name;
        public String notice;
        public boolean open;
        public BigDecimal minDeliveryAmount;
        public BigDecimal deliveryFee;
        public double rating;
        public List<String> tags;
        public String category;
        public String area;
        public String logoText;
        public int avgDeliveryMinutes;
        public double distanceKm;
        public int monthlySales;
        public String statusMessage;
        public String deliveryGuarantee;
        public List<String> promotions;
        public List<String> couponHints;
        public int deliveryPriority;
        public String businessHours;
        public double deliveryRangeKm;

        public Store(long id, long merchantId, String name, String notice, boolean open, BigDecimal minDeliveryAmount, BigDecimal deliveryFee, double rating, List<String> tags) {
            this(id, merchantId, name, notice, open, minDeliveryAmount, deliveryFee, rating, tags, "美食", "附近", name.substring(0, 1), 35, 2.0, 1000, notice, "准时宝", List.of(), List.of(), 50);
        }

        public Store(long id, long merchantId, String name, String notice, boolean open, BigDecimal minDeliveryAmount, BigDecimal deliveryFee, double rating, List<String> tags,
                     String category, String area, String logoText, int avgDeliveryMinutes, double distanceKm, int monthlySales, String statusMessage,
                     String deliveryGuarantee, List<String> promotions, List<String> couponHints, int deliveryPriority) {
            this(id, merchantId, name, notice, open, minDeliveryAmount, deliveryFee, rating, tags, category, area, logoText,
                    avgDeliveryMinutes, distanceKm, monthlySales, statusMessage, deliveryGuarantee, promotions, couponHints,
                    deliveryPriority, "09:00-22:00", Math.max(3.0, distanceKm + 3.0));
        }

        public Store(long id, long merchantId, String name, String notice, boolean open, BigDecimal minDeliveryAmount, BigDecimal deliveryFee, double rating, List<String> tags,
                     String category, String area, String logoText, int avgDeliveryMinutes, double distanceKm, int monthlySales, String statusMessage,
                     String deliveryGuarantee, List<String> promotions, List<String> couponHints, int deliveryPriority,
                     String businessHours, double deliveryRangeKm) {
            this.id = id;
            this.merchantId = merchantId;
            this.name = name;
            this.notice = notice;
            this.open = open;
            this.minDeliveryAmount = minDeliveryAmount;
            this.deliveryFee = deliveryFee;
            this.rating = rating;
            this.tags = tags;
            this.category = category;
            this.area = area;
            this.logoText = logoText;
            this.avgDeliveryMinutes = avgDeliveryMinutes;
            this.distanceKm = distanceKm;
            this.monthlySales = monthlySales;
            this.statusMessage = statusMessage;
            this.deliveryGuarantee = deliveryGuarantee;
            this.promotions = promotions;
            this.couponHints = couponHints;
            this.deliveryPriority = deliveryPriority;
            this.businessHours = businessHours;
            this.deliveryRangeKm = deliveryRangeKm;
        }
    }

    public static class Product {
        public long id;
        public long storeId;
        public String name;
        public String description;
        public BigDecimal price;
        public int stock;
        public boolean onSale;
        public int monthlySales;
        public String category;
        public String imageTone;
        public int ranking;
        public String discountLabel;
        public BigDecimal originalPrice;

        public Product(long id, long storeId, String name, String description, BigDecimal price, int stock, boolean onSale, int monthlySales) {
            this(id, storeId, name, description, price, stock, onSale, monthlySales, "热销", "default", 99, "", price);
        }

        public Product(long id, long storeId, String name, String description, BigDecimal price, int stock, boolean onSale, int monthlySales,
                       String category, String imageTone, int ranking, String discountLabel, BigDecimal originalPrice) {
            this.id = id;
            this.storeId = storeId;
            this.name = name;
            this.description = description;
            this.price = price;
            this.stock = stock;
            this.onSale = onSale;
            this.monthlySales = monthlySales;
            this.category = category;
            this.imageTone = imageTone;
            this.ranking = ranking;
            this.discountLabel = discountLabel;
            this.originalPrice = originalPrice;
        }

        public boolean available() {
            return onSale && stock > 0;
        }
    }

    public static class CartItem {
        public long id;
        public long productId;
        public long storeId;
        public String productName;
        public BigDecimal price;
        public int quantity;
        public BigDecimal subtotal;
        public boolean available = true;

        public CartItem(long id, long productId, long storeId, String productName, BigDecimal price, int quantity) {
            this.id = id;
            this.productId = productId;
            this.storeId = storeId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.subtotal = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static class Order {
        public long id;
        public String orderNo;
        public long customerId;
        public String customerName;
        public long storeId;
        public String storeName;
        public OrderStatus status;
        public BigDecimal itemAmount;
        public BigDecimal deliveryFee;
        public BigDecimal discountAmount;
        public BigDecimal payAmount;
        public String idempotencyToken;
        public String couponCode;
        public String paymentOrderNo;
        public Long deliveryId;
        public Long refundId;
        public AddressSnapshot addressSnapshot;
        public List<OrderItem> items = new ArrayList<>();
        public List<OrderStatusRecord> statusRecords = new ArrayList<>();
        public Review review;
        public LocalDateTime createdAt;
        public LocalDateTime paidAt;
        public LocalDateTime finishedAt;
        public int estimatedDeliveryMinutes;
        public List<FulfillmentStep> fulfillmentSteps = new ArrayList<>();
    }

    public static class FulfillmentStep {
        public long id;
        public long orderId;
        public String title;
        public String status;
        public String operator;
        public String detail;
        public LocalDateTime createdAt;

        public FulfillmentStep(long id, long orderId, String title, String status, String operator, String detail, LocalDateTime createdAt) {
            this.id = id;
            this.orderId = orderId;
            this.title = title;
            this.status = status;
            this.operator = operator;
            this.detail = detail;
            this.createdAt = createdAt;
        }
    }

    public static class AddressSnapshot {
        public String receiver;
        public String phoneMasked;
        public String detailMasked;
        public double distanceKm;

        public AddressSnapshot(String receiver, String phoneMasked, String detailMasked, double distanceKm) {
            this.receiver = receiver;
            this.phoneMasked = phoneMasked;
            this.detailMasked = detailMasked;
            this.distanceKm = distanceKm;
        }
    }

    public static class OrderItem {
        public long productId;
        public String productName;
        public BigDecimal price;
        public int quantity;
        public BigDecimal subtotal;

        public OrderItem(long productId, String productName, BigDecimal price, int quantity, BigDecimal subtotal) {
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }

    public static class OrderStatusRecord {
        public long id;
        public long orderId;
        public OrderStatus beforeStatus;
        public OrderStatus afterStatus;
        public String operator;
        public String reason;
        public LocalDateTime createdAt;

        public OrderStatusRecord(long id, long orderId, OrderStatus beforeStatus, OrderStatus afterStatus, String operator, String reason, LocalDateTime createdAt) {
            this.id = id;
            this.orderId = orderId;
            this.beforeStatus = beforeStatus;
            this.afterStatus = afterStatus;
            this.operator = operator;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }

    public static class InventoryReservation {
        public long id;
        public long orderId;
        public long productId;
        public String productName;
        public int quantity;
        public String status;
        public LocalDateTime createdAt;

        public InventoryReservation(long id, long orderId, long productId, String productName, int quantity, String status, LocalDateTime createdAt) {
            this.id = id;
            this.orderId = orderId;
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    public static class PaymentOrder {
        public long id;
        public String paymentNo;
        public long orderId;
        public String status;
        public BigDecimal amount;
        public String callbackFlowNo;
        public LocalDateTime paidAt;

        public PaymentOrder(long id, String paymentNo, long orderId, String status, BigDecimal amount, String callbackFlowNo, LocalDateTime paidAt) {
            this.id = id;
            this.paymentNo = paymentNo;
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
            this.callbackFlowNo = callbackFlowNo;
            this.paidAt = paidAt;
        }
    }

    public static class RefundOrder {
        public long id;
        public String refundNo;
        public long orderId;
        public String status;
        public BigDecimal amount;
        public String reason;
        public LocalDateTime createdAt;
        public LocalDateTime finishedAt;

        public RefundOrder(long id, String refundNo, long orderId, String status, BigDecimal amount, String reason, LocalDateTime createdAt, LocalDateTime finishedAt) {
            this.id = id;
            this.refundNo = refundNo;
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
            this.reason = reason;
            this.createdAt = createdAt;
            this.finishedAt = finishedAt;
        }
    }

    public static class DeliveryOrder {
        public long id;
        public long orderId;
        public Long riderId;
        public String riderName;
        public String status;
        public String currentStep;
        public String exceptionReason;
        public LocalDateTime createdAt;
        public LocalDateTime deliveredAt;
        public List<DeliveryStatusLog> statusLogs = new ArrayList<>();

        public DeliveryOrder(long id, long orderId, Long riderId, String riderName, String status, String currentStep, String exceptionReason, LocalDateTime createdAt, LocalDateTime deliveredAt) {
            this.id = id;
            this.orderId = orderId;
            this.riderId = riderId;
            this.riderName = riderName;
            this.status = status;
            this.currentStep = currentStep;
            this.exceptionReason = exceptionReason;
            this.createdAt = createdAt;
            this.deliveredAt = deliveredAt;
        }
    }

    public static class DeliveryStatusLog {
        public long id;
        public long deliveryId;
        public long orderId;
        public Long riderId;
        public String riderName;
        public String beforeStatus;
        public String afterStatus;
        public String operatorName;
        public String detail;
        public String location;
        public LocalDateTime createdAt;

        public DeliveryStatusLog(long id, long deliveryId, long orderId, Long riderId, String riderName,
                                 String beforeStatus, String afterStatus, String operatorName, String detail,
                                 String location, LocalDateTime createdAt) {
            this.id = id;
            this.deliveryId = deliveryId;
            this.orderId = orderId;
            this.riderId = riderId;
            this.riderName = riderName;
            this.beforeStatus = beforeStatus;
            this.afterStatus = afterStatus;
            this.operatorName = operatorName;
            this.detail = detail;
            this.location = location;
            this.createdAt = createdAt;
        }
    }

    public static class Review {
        public long id;
        public long orderId;
        public long customerId;
        public int score;
        public String content;
        public String merchantReply;
        public LocalDateTime repliedAt;
        public LocalDateTime createdAt;

        public Review(long id, long orderId, long customerId, int score, String content, LocalDateTime createdAt) {
            this(id, orderId, customerId, score, content, null, null, createdAt);
        }

        public Review(long id, long orderId, long customerId, int score, String content,
                      String merchantReply, LocalDateTime repliedAt, LocalDateTime createdAt) {
            this.id = id;
            this.orderId = orderId;
            this.customerId = customerId;
            this.score = score;
            this.content = content;
            this.merchantReply = merchantReply;
            this.repliedAt = repliedAt;
            this.createdAt = createdAt;
        }
    }

    public static class Ticket {
        public long id;
        public long orderId;
        public long customerId;
        public String status;
        public String reason;
        public String result;
        public LocalDateTime createdAt;
        public LocalDateTime finishedAt;

        public Ticket(long id, long orderId, long customerId, String status, String reason, String result, LocalDateTime createdAt, LocalDateTime finishedAt) {
            this.id = id;
            this.orderId = orderId;
            this.customerId = customerId;
            this.status = status;
            this.reason = reason;
            this.result = result;
            this.createdAt = createdAt;
            this.finishedAt = finishedAt;
        }
    }

    public static class RiskRecord {
        public long id;
        public String type;
        public String objectType;
        public long objectId;
        public String reason;
        public String status;
        public LocalDateTime createdAt;

        public RiskRecord(long id, String type, String objectType, long objectId, String reason, String status, LocalDateTime createdAt) {
            this.id = id;
            this.type = type;
            this.objectType = objectType;
            this.objectId = objectId;
            this.reason = reason;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    public static class AuditLog {
        public long id;
        public long actorId;
        public String actorName;
        public Role actorRole;
        public String action;
        public String objectType;
        public long objectId;
        public String beforeStatus;
        public String afterStatus;
        public String reason;
        public LocalDateTime createdAt;

        public AuditLog(long id, long actorId, String actorName, Role actorRole, String action, String objectType, long objectId, String beforeStatus, String afterStatus, String reason, LocalDateTime createdAt) {
            this.id = id;
            this.actorId = actorId;
            this.actorName = actorName;
            this.actorRole = actorRole;
            this.action = action;
            this.objectType = objectType;
            this.objectId = objectId;
            this.beforeStatus = beforeStatus;
            this.afterStatus = afterStatus;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }

    public static class OutboxEvent {
        public long id;
        public String eventType;
        public String aggregateType;
        public long aggregateId;
        public String payload;
        public String status;
        public int retryCount;
        public LocalDateTime createdAt;

        public OutboxEvent(long id, String eventType, String aggregateType, long aggregateId, String payload, String status, int retryCount, LocalDateTime createdAt) {
            this.id = id;
            this.eventType = eventType;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            this.payload = payload;
            this.status = status;
            this.retryCount = retryCount;
            this.createdAt = createdAt;
        }
    }

    public static class OutboxConsumeRecord {
        public long eventId;
        public String consumerName;
        public String eventType;
        public String aggregateType;
        public long aggregateId;
        public String status;
        public LocalDateTime consumedAt;

        public OutboxConsumeRecord(long eventId, String consumerName, String eventType, String aggregateType,
                                   long aggregateId, String status, LocalDateTime consumedAt) {
            this.eventId = eventId;
            this.consumerName = consumerName;
            this.eventType = eventType;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            this.status = status;
            this.consumedAt = consumedAt;
        }
    }

    public static class MerchantNotification {
        public long id;
        public long eventId;
        public long orderId;
        public long storeId;
        public String title;
        public String message;
        public LocalDateTime createdAt;

        public MerchantNotification(long id, long eventId, long orderId, long storeId, String title, String message, LocalDateTime createdAt) {
            this.id = id;
            this.eventId = eventId;
            this.orderId = orderId;
            this.storeId = storeId;
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
        }
    }
}
