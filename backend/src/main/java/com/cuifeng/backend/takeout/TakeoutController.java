package com.cuifeng.backend.takeout;

import com.cuifeng.backend.auth.Role;
import com.cuifeng.backend.common.ApiResponse;
import com.cuifeng.backend.takeout.TakeoutService.Account;
import com.cuifeng.backend.takeout.TakeoutService.AddressRequest;
import com.cuifeng.backend.takeout.TakeoutService.AddCartItemRequest;
import com.cuifeng.backend.takeout.TakeoutService.AdminTicketRequest;
import com.cuifeng.backend.takeout.TakeoutService.CreateOrderRequest;
import com.cuifeng.backend.takeout.TakeoutService.CouponClaimRequest;
import com.cuifeng.backend.takeout.TakeoutService.CustomerRegisterRequest;
import com.cuifeng.backend.takeout.TakeoutService.LoginRequest;
import com.cuifeng.backend.takeout.TakeoutService.MerchantProductRequest;
import com.cuifeng.backend.takeout.TakeoutService.OnboardingApplicationRequest;
import com.cuifeng.backend.takeout.TakeoutService.OnboardingApprovalRequest;
import com.cuifeng.backend.takeout.TakeoutService.PayRequest;
import com.cuifeng.backend.takeout.TakeoutService.RefundRequest;
import com.cuifeng.backend.takeout.TakeoutService.ReviewReplyRequest;
import com.cuifeng.backend.takeout.TakeoutService.ReviewRequest;
import com.cuifeng.backend.takeout.TakeoutService.RiderExceptionRequest;
import com.cuifeng.backend.takeout.TakeoutService.StoreConfigRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@RestController
public class TakeoutController {
    private final TakeoutService service;

    public TakeoutController(TakeoutService service) {
        this.service = service;
    }

    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request));
    }

    @PostMapping("/auth/register/customer")
    public ApiResponse<?> registerCustomer(@RequestBody CustomerRegisterRequest request) {
        return ApiResponse.ok(service.registerCustomer(request));
    }

    @GetMapping("/auth/me")
    public ApiResponse<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.me(authorization));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(authorization);
        return ApiResponse.ok("已退出", null);
    }

    @PostMapping("/auth/logout-all")
    public ApiResponse<?> logoutAll(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logoutAll(authorization);
        return ApiResponse.ok("已退出全部设备", null);
    }

    @PostMapping("/onboarding/merchant-applications")
    public ApiResponse<?> submitMerchantApplication(@RequestBody OnboardingApplicationRequest request) {
        return ApiResponse.ok(service.submitMerchantApplication(request));
    }

    @PostMapping("/onboarding/rider-applications")
    public ApiResponse<?> submitRiderApplication(@RequestBody OnboardingApplicationRequest request) {
        return ApiResponse.ok(service.submitRiderApplication(request));
    }

    @GetMapping("/user/addresses")
    public ApiResponse<?> userAddresses(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userAddresses(customer(authorization)));
    }

    @GetMapping("/user/profile")
    public ApiResponse<?> userProfile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userProfile(customer(authorization)));
    }

    @GetMapping("/user/coupons")
    public ApiResponse<?> userCoupons(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userCoupons(customer(authorization)));
    }

    @GetMapping("/user/reviews")
    public ApiResponse<?> userReviews(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userReviews(customer(authorization)));
    }

    @GetMapping("/user/after-sales")
    public ApiResponse<?> userAfterSales(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userTickets(customer(authorization)));
    }

    @PostMapping("/user/addresses")
    public ApiResponse<?> createUserAddress(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody AddressRequest request) {
        return ApiResponse.ok(service.createAddress(customer(authorization), request));
    }

    @PatchMapping("/user/addresses/{id}")
    public ApiResponse<?> updateUserAddress(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable long id,
                                            @RequestBody AddressRequest request) {
        return ApiResponse.ok(service.updateAddress(customer(authorization), id, request));
    }

    @DeleteMapping("/user/addresses/{id}")
    public ApiResponse<?> deleteUserAddress(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable long id) {
        return ApiResponse.ok(service.deleteAddress(customer(authorization), id));
    }

    @PostMapping("/user/addresses/{id}/default")
    public ApiResponse<?> setDefaultUserAddress(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable long id) {
        return ApiResponse.ok(service.setDefaultAddress(customer(authorization), id));
    }

    @GetMapping("/user/orders")
    public ApiResponse<?> userOrders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userOrders(customer(authorization)));
    }

    @GetMapping("/user/orders/{id}")
    public ApiResponse<?> userOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable long id) {
        return ApiResponse.ok(service.userOrder(customer(authorization), id));
    }

    @PostMapping("/user/orders/{id}/pay")
    public ApiResponse<?> payOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @PathVariable long id,
                                   @RequestBody(required = false) PayRequest request) {
        return ApiResponse.ok(service.payOrder(customer(authorization), id, request == null ? new PayRequest() : request));
    }

    @PostMapping("/user/orders/{id}/refund")
    public ApiResponse<?> refundOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long id,
                                      @RequestBody(required = false) RefundRequest request) {
        return ApiResponse.ok(service.refundOrder(customer(authorization), id, request == null ? new RefundRequest() : request));
    }

    @PostMapping("/user/orders/{id}/confirm")
    public ApiResponse<?> confirmOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable long id) {
        return ApiResponse.ok(service.confirmOrder(customer(authorization), id));
    }

    @PostMapping("/user/orders/{id}/review")
    public ApiResponse<?> reviewOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long id,
                                      @RequestBody ReviewRequest request) {
        return ApiResponse.ok(service.reviewOrder(customer(authorization), id, request));
    }

    @GetMapping("/market/home")
    public ApiResponse<?> marketHome() {
        return ApiResponse.ok(service.marketHome());
    }

    @GetMapping("/stores")
    public ApiResponse<?> stores(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.stores(keyword, category, sort));
    }

    @GetMapping("/stores/page")
    public ApiResponse<?> storesPage(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(service.storesPage(keyword, category, sort, page, size));
    }

    @GetMapping("/stores/{id}")
    public ApiResponse<?> storeDetail(@PathVariable long id) {
        return ApiResponse.ok(service.storeDetail(id));
    }

    @GetMapping("/stores/{id}/products")
    public ApiResponse<?> products(@PathVariable long id) {
        return ApiResponse.ok(service.products(id));
    }

    @GetMapping("/stores/{id}/reviews")
    public ApiResponse<?> storeReviews(@PathVariable long id) {
        return ApiResponse.ok(service.storeReviews(id));
    }

    @GetMapping("/cart/items")
    public ApiResponse<?> cartItems(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.cartItems(customer(authorization)));
    }

    @PostMapping("/cart/items")
    public ApiResponse<?> addCartItem(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(service.addCartItem(customer(authorization), request));
    }

    @DeleteMapping("/cart/items/{productId}")
    public ApiResponse<?> removeCartItem(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable long productId) {
        return ApiResponse.ok(service.removeCartItem(customer(authorization), productId));
    }

    @GetMapping("/orders/idempotency-token")
    public ApiResponse<?> idempotencyToken(@RequestHeader(value = "Authorization", required = false) String authorization) {
        customer(authorization);
        return ApiResponse.ok(Map.of("token", UUID.randomUUID().toString()));
    }

    @PostMapping("/orders")
    public ApiResponse<?> createOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(service.createOrder(customer(authorization), request));
    }

    @PostMapping("/marketing/coupons/{sceneCode}/claim")
    public ApiResponse<?> claimCoupon(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable String sceneCode,
                                      @RequestBody(required = false) CouponClaimRequest request,
                                      HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.claimCoupon(customer(authorization), sceneCode,
                request == null ? new CouponClaimRequest() : request,
                clientIp(servletRequest)));
    }

    @GetMapping("/merchant/accounts/page")
    public ApiResponse<?> merchantAccountsPage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        admin(authorization);
        return ApiResponse.ok(service.merchantAccountsPage(keyword, page, size));
    }

    @GetMapping("/merchant/products")
    public ApiResponse<?> merchantProducts(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantProducts(merchant(authorization)));
    }

    @GetMapping("/merchant/store")
    public ApiResponse<?> merchantStore(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantStore(merchant(authorization)));
    }

    @PatchMapping("/merchant/store")
    public ApiResponse<?> updateMerchantStore(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody StoreConfigRequest request) {
        return ApiResponse.ok(service.updateMerchantStore(merchant(authorization), request));
    }

    @PostMapping("/merchant/store/open")
    public ApiResponse<?> openMerchantStore(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.setMerchantStoreOpen(merchant(authorization), true));
    }

    @PostMapping("/merchant/store/close")
    public ApiResponse<?> closeMerchantStore(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.setMerchantStoreOpen(merchant(authorization), false));
    }

    @GetMapping("/merchant/products/page")
    public ApiResponse<?> merchantProductsPage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(service.merchantProductsPage(merchant(authorization), page, size));
    }

    @PostMapping("/merchant/products")
    public ApiResponse<?> saveMerchantProduct(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody MerchantProductRequest request) {
        return ApiResponse.ok(service.saveMerchantProduct(merchant(authorization), request));
    }

    @PatchMapping("/merchant/products/{id}")
    public ApiResponse<?> updateMerchantProduct(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable long id,
                                                @RequestBody MerchantProductRequest request) {
        return ApiResponse.ok(service.updateMerchantProduct(merchant(authorization), id, request));
    }

    @GetMapping("/merchant/orders")
    public ApiResponse<?> merchantOrders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantOrders(merchant(authorization)));
    }

    @GetMapping("/merchant/orders/page")
    public ApiResponse<?> merchantOrdersPage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(service.merchantOrdersPage(merchant(authorization), page, size));
    }

    @GetMapping("/merchant/orders/pending")
    public ApiResponse<?> merchantPendingOrders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantPendingOrders(merchant(authorization)));
    }

    @PostMapping("/merchant/orders/{id}/accept")
    public ApiResponse<?> merchantAccept(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable long id) {
        return ApiResponse.ok(service.merchantAccept(merchant(authorization), id));
    }

    @PostMapping("/merchant/orders/{id}/reject")
    public ApiResponse<?> merchantReject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable long id,
                                         @RequestBody(required = false) RefundRequest request) {
        return ApiResponse.ok(service.merchantReject(merchant(authorization), id, request == null ? new RefundRequest() : request));
    }

    @PostMapping("/merchant/orders/{id}/ready")
    public ApiResponse<?> merchantReady(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable long id) {
        return ApiResponse.ok(service.merchantReady(merchant(authorization), id));
    }

    @GetMapping("/merchant/reviews")
    public ApiResponse<?> merchantReviews(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantReviews(merchant(authorization)));
    }

    @PostMapping("/merchant/reviews/{id}/reply")
    public ApiResponse<?> merchantReplyReview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable long id,
                                              @RequestBody ReviewReplyRequest request) {
        return ApiResponse.ok(service.replyMerchantReview(merchant(authorization), id, request));
    }

    @GetMapping("/merchant/after-sales")
    public ApiResponse<?> merchantAfterSales(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.merchantTickets(merchant(authorization)));
    }

    @GetMapping("/rider/tasks")
    public ApiResponse<?> riderTasks(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.riderTasks(rider(authorization)));
    }

    @GetMapping("/rider/tasks/available")
    public ApiResponse<?> availableRiderTasks(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.availableRiderTasks(rider(authorization)));
    }

    @PostMapping("/rider/tasks/{id}/accept")
    public ApiResponse<?> riderAccept(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long id) {
        return ApiResponse.ok(service.riderAccept(rider(authorization), id));
    }

    @PostMapping("/rider/tasks/{id}/arrive-store")
    public ApiResponse<?> riderArriveStore(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable long id) {
        return ApiResponse.ok(service.riderArriveStore(rider(authorization), id));
    }

    @PostMapping("/rider/tasks/{id}/pickup")
    public ApiResponse<?> riderPickup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long id) {
        return ApiResponse.ok(service.riderPickup(rider(authorization), id));
    }

    @PostMapping("/rider/tasks/{id}/deliver")
    public ApiResponse<?> riderDeliver(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable long id) {
        return ApiResponse.ok(service.riderDeliver(rider(authorization), id));
    }

    @PostMapping("/rider/tasks/{id}/exception")
    public ApiResponse<?> riderException(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable long id,
                                         @RequestBody(required = false) RiderExceptionRequest request) {
        return ApiResponse.ok(service.riderException(rider(authorization), id, request == null ? new RiderExceptionRequest() : request));
    }

    @GetMapping("/admin/orders")
    public ApiResponse<?> adminOrders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.adminOrders());
    }

    @GetMapping("/admin/tickets")
    public ApiResponse<?> adminTickets(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.adminTickets());
    }

    @PostMapping("/admin/tickets/{id}/approve")
    public ApiResponse<?> adminApproveTicket(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @PathVariable long id,
                                             @RequestBody(required = false) AdminTicketRequest request) {
        return ApiResponse.ok(service.adminApproveTicket(admin(authorization), id, request == null ? new AdminTicketRequest() : request));
    }

    @PostMapping("/admin/tickets/{id}/reject")
    public ApiResponse<?> adminRejectTicket(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable long id,
                                            @RequestBody(required = false) AdminTicketRequest request) {
        return ApiResponse.ok(service.adminRejectTicket(admin(authorization), id, request == null ? new AdminTicketRequest() : request));
    }

    @GetMapping("/admin/risk-records")
    public ApiResponse<?> adminRiskRecords(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.adminRiskRecords());
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<?> adminAuditLogs(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.adminAuditLogs());
    }

    @GetMapping("/admin/coupon-activities")
    public ApiResponse<?> adminCouponActivities(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.adminCouponActivities());
    }

    @GetMapping("/admin/coupons/page")
    public ApiResponse<?> adminCoupons(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        admin(authorization);
        return ApiResponse.ok(service.adminCoupons(status, keyword, page, size));
    }

    @GetMapping("/admin/coupon-status-logs")
    public ApiResponse<?> adminCouponStatusLogs(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestParam(required = false) Long couponId,
                                                @RequestParam(defaultValue = "50") int limit) {
        admin(authorization);
        return ApiResponse.ok(service.adminCouponStatusLogs(couponId, limit));
    }

    @GetMapping("/admin/onboarding-applications")
    public ApiResponse<?> adminOnboardingApplications(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestParam(required = false) String status) {
        admin(authorization);
        return ApiResponse.ok(service.adminOnboardingApplications(status));
    }

    @PostMapping("/admin/onboarding-applications/{id}/approve")
    public ApiResponse<?> approveOnboardingApplication(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @PathVariable long id,
                                                       @RequestBody(required = false) OnboardingApprovalRequest request) {
        return ApiResponse.ok(service.approveOnboardingApplication(admin(authorization), id,
                request == null ? new OnboardingApprovalRequest() : request));
    }

    @PostMapping("/admin/onboarding-applications/{id}/reject")
    public ApiResponse<?> rejectOnboardingApplication(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable long id,
                                                      @RequestBody(required = false) OnboardingApprovalRequest request) {
        return ApiResponse.ok(service.rejectOnboardingApplication(admin(authorization), id,
                request == null ? new OnboardingApprovalRequest() : request));
    }

    @GetMapping("/demo/snapshot")
    public ApiResponse<?> snapshot(@RequestHeader(value = "Authorization", required = false) String authorization) {
        admin(authorization);
        return ApiResponse.ok(service.snapshot());
    }

    private Account customer(String authorization) {
        return service.require(authorization, Role.CUSTOMER);
    }

    private Account merchant(String authorization) {
        return service.require(authorization, Role.MERCHANT);
    }

    private Account rider(String authorization) {
        return service.require(authorization, Role.RIDER);
    }

    private Account admin(String authorization) {
        return service.require(authorization, Role.ADMIN);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
