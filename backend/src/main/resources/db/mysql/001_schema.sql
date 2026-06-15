CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    store_id BIGINT NULL,
    phone VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS onboarding_application (
    id BIGINT PRIMARY KEY,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    applicant_name VARCHAR(128) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    store_name VARCHAR(128) NOT NULL DEFAULT '',
    category VARCHAR(64) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    preferred_username VARCHAR(64) NOT NULL DEFAULT '',
    reason VARCHAR(255) NOT NULL,
    result VARCHAR(255) NOT NULL,
    created_account_id BIGINT NULL,
    created_store_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_onboarding_role_status (role, status, created_at),
    INDEX idx_onboarding_phone (phone),
    CONSTRAINT fk_onboarding_account FOREIGN KEY (created_account_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_address (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver VARCHAR(64) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    detail_masked VARCHAR(255) NOT NULL,
    distance_km DECIMAL(8,2) NOT NULL,
    in_range BOOLEAN NOT NULL,
    default_address BOOLEAN NOT NULL,
    INDEX idx_address_user (user_id),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS channel (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    subtitle VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS banner (
    scene_code VARCHAR(32) PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NOT NULL,
    action_text VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store (
    id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    notice VARCHAR(255) NOT NULL,
    open_flag BOOLEAN NOT NULL,
    min_delivery_amount DECIMAL(10,2) NOT NULL,
    delivery_fee DECIMAL(10,2) NOT NULL,
    rating DECIMAL(3,1) NOT NULL,
    category VARCHAR(64) NOT NULL,
    area VARCHAR(64) NOT NULL,
    logo_text VARCHAR(16) NOT NULL,
    avg_delivery_minutes INT NOT NULL,
    distance_km DECIMAL(8,2) NOT NULL,
    monthly_sales INT NOT NULL,
    status_message VARCHAR(255) NOT NULL,
    delivery_guarantee VARCHAR(64) NOT NULL,
    delivery_priority INT NOT NULL,
    business_hours VARCHAR(64) NOT NULL DEFAULT '09:00-22:00',
    delivery_range_km DECIMAL(8,2) NOT NULL DEFAULT 6.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_store_merchant (merchant_id),
    INDEX idx_store_category_sales (category, monthly_sales),
    INDEX idx_store_distance (distance_km),
    CONSTRAINT fk_store_merchant FOREIGN KEY (merchant_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store_tag (
    store_id BIGINT NOT NULL,
    tag VARCHAR(64) NOT NULL,
    PRIMARY KEY (store_id, tag),
    CONSTRAINT fk_store_tag_store FOREIGN KEY (store_id) REFERENCES store(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store_promotion (
    store_id BIGINT NOT NULL,
    promotion VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, promotion),
    CONSTRAINT fk_store_promotion_store FOREIGN KEY (store_id) REFERENCES store(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store_coupon_hint (
    store_id BIGINT NOT NULL,
    coupon_hint VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, coupon_hint),
    CONSTRAINT fk_store_coupon_store FOREIGN KEY (store_id) REFERENCES store(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    on_sale BOOLEAN NOT NULL,
    monthly_sales INT NOT NULL,
    category VARCHAR(64) NOT NULL,
    image_tone VARCHAR(64) NOT NULL,
    ranking INT NOT NULL,
    discount_label VARCHAR(64) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_store (store_id),
    INDEX idx_product_store_category (store_id, category),
    INDEX idx_product_sales (monthly_sales),
    FULLTEXT INDEX ft_product_search (name, description),
    CONSTRAINT fk_product_store FOREIGN KEY (store_id) REFERENCES store(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    INDEX idx_cart_user_store (user_id, store_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_cart_store FOREIGN KEY (store_id) REFERENCES store(id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_activity (
    batch_code VARCHAR(64) PRIMARY KEY,
    scene_code VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    per_user_limit INT NOT NULL,
    stock INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_activity_scene_batch (scene_code, batch_code),
    INDEX idx_coupon_activity_scene_time (scene_code, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_template (
    batch_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL,
    threshold_amount DECIMAL(10,2) NOT NULL,
    scope VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    PRIMARY KEY (batch_code, template_code),
    CONSTRAINT fk_coupon_template_activity FOREIGN KEY (batch_code) REFERENCES coupon_activity(batch_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_coupon (
    id BIGINT PRIMARY KEY,
    coupon_code VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    scene_code VARCHAR(32) NOT NULL,
    batch_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL,
    threshold_amount DECIMAL(10,2) NOT NULL,
    scope VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    locked_order_id BIGINT NULL,
    used_order_id BIGINT NULL,
    reason VARCHAR(255) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_coupon_user_batch_template (user_id, batch_code, template_code),
    INDEX idx_user_coupon_user_status (user_id, status, valid_to),
    INDEX idx_user_coupon_batch_status (batch_code, status),
    INDEX idx_user_coupon_locked_order (locked_order_id),
    INDEX idx_user_coupon_used_order (used_order_id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_user_coupon_template FOREIGN KEY (batch_code, template_code) REFERENCES coupon_template(batch_code, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_status_log (
    id BIGINT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    coupon_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    before_status VARCHAR(32) NULL,
    after_status VARCHAR(32) NOT NULL,
    related_order_id BIGINT NULL,
    operator_name VARCHAR(128) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_coupon_log_coupon (coupon_id, created_at),
    INDEX idx_coupon_log_user (user_id, created_at),
    INDEX idx_coupon_log_order (related_order_id, created_at),
    CONSTRAINT fk_coupon_log_coupon FOREIGN KEY (coupon_id) REFERENCES user_coupon(id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_log_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_main (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    store_id BIGINT NOT NULL,
    store_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    item_amount DECIMAL(10,2) NOT NULL,
    delivery_fee DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL,
    pay_amount DECIMAL(10,2) NOT NULL,
    idempotency_token VARCHAR(128) NOT NULL,
    coupon_code VARCHAR(64) NULL,
    payment_order_no VARCHAR(64) NULL,
    delivery_id BIGINT NULL,
    refund_id BIGINT NULL,
    receiver VARCHAR(64) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    detail_masked VARCHAR(255) NOT NULL,
    distance_km DECIMAL(8,2) NOT NULL,
    estimated_delivery_minutes INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_order_customer_created (customer_id, created_at),
    INDEX idx_order_store_status (store_id, status),
    UNIQUE KEY uk_order_idempotency (customer_id, idempotency_token),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES user_account(id),
    CONSTRAINT fk_order_store FOREIGN KEY (store_id) REFERENCES store(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_item (
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_status_record (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    before_status VARCHAR(32) NULL,
    after_status VARCHAR(32) NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_status_order (order_id, created_at),
    CONSTRAINT fk_status_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fulfillment_step (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_fulfillment_order (order_id, created_at),
    CONSTRAINT fk_fulfillment_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_reservation_order (order_id),
    INDEX idx_reservation_product_status (product_id, status),
    CONSTRAINT fk_reservation_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    callback_flow_no VARCHAR(128) NULL,
    paid_at TIMESTAMP NULL,
    INDEX idx_payment_order (order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS refund_order (
    id BIGINT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_refund_order (order_id),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS delivery_order (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    rider_id BIGINT NULL,
    rider_name VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(255) NOT NULL,
    exception_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP NULL,
    INDEX idx_delivery_rider_status (rider_id, status),
    INDEX idx_delivery_order (order_id),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_rider FOREIGN KEY (rider_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS delivery_status_log (
    id BIGINT PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    rider_id BIGINT NULL,
    rider_name VARCHAR(128) NULL,
    before_status VARCHAR(32) NULL,
    after_status VARCHAR(32) NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    location VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_delivery_log_delivery (delivery_id, created_at),
    INDEX idx_delivery_log_order (order_id, created_at),
    CONSTRAINT fk_delivery_log_delivery FOREIGN KEY (delivery_id) REFERENCES delivery_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_log_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_log_rider FOREIGN KEY (rider_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    score INT NOT NULL,
    content VARCHAR(500) NULL,
    merchant_reply VARCHAR(500) NULL,
    merchant_replied_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_review_order (order_id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS customer_service_ticket (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    result VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_ticket_status (status, created_at),
    CONSTRAINT fk_ticket_order FOREIGN KEY (order_id) REFERENCES order_main(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS risk_record (
    id BIGINT PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_risk_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(128) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT NOT NULL,
    before_status VARCHAR(64) NOT NULL,
    after_status VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_audit_object (object_type, object_id),
    INDEX idx_audit_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_outbox_status (status, created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_consume_log (
    event_id BIGINT NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    consumed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_name),
    INDEX idx_outbox_consume_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS merchant_notification (
    id BIGINT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_merchant_notification_event (event_id),
    INDEX idx_merchant_notification_store (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_callback_flow (
    callback_flow_no VARCHAR(128) PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
