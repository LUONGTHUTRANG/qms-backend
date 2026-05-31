ticket--- MANAGEMENT SERVICE ---
DROP DATABASE IF EXISTS bank_qms_management;
CREATE DATABASE bank_qms_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bank_qms_management;

-- 1. Các bảng Master Data (Không thay đổi)
CREATE TABLE branch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE customer_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    base_priority_score INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE request_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_segment_id BIGINT NOT NULL, -- Thêm mới để gắn cứng vào 1 segment
    code VARCHAR(50) NOT NULL UNIQUE,
    prefix_code VARCHAR(5) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rg_segment FOREIGN KEY (customer_segment_id) REFERENCES customer_segment(id)
) ENGINE=InnoDB;

CREATE TABLE service_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_group_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    average_service_minutes INT NOT NULL DEFAULT 10,
    priority_weight INT NOT NULL DEFAULT 0,
    sla_minutes INT NOT NULL DEFAULT 15,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_type_rg FOREIGN KEY (request_group_id) REFERENCES request_group(id)
) ENGINE=InnoDB;

CREATE TABLE service_counter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status ENUM('AVAILABLE', 'OCCUPIED', 'OFFLINE') NOT NULL DEFAULT 'AVAILABLE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (branch_id, code),
    CONSTRAINT fk_counter_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
) ENGINE=InnoDB;

CREATE TABLE counter_request_group (
    counter_id BIGINT NOT NULL,
    request_group_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (counter_id, request_group_id),
    CONSTRAINT fk_crg_counter FOREIGN KEY (counter_id) REFERENCES service_counter(id),
    CONSTRAINT fk_crg_rg FOREIGN KEY (request_group_id) REFERENCES request_group(id)
) ENGINE=InnoDB;

CREATE TABLE counter_customer_segment (
    counter_id BIGINT NOT NULL,
    customer_segment_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (counter_id, customer_segment_id),
    CONSTRAINT fk_ccs_counter FOREIGN KEY (counter_id) REFERENCES service_counter(id),
    CONSTRAINT fk_ccs_cs FOREIGN KEY (customer_segment_id) REFERENCES customer_segment(id)
) ENGINE=InnoDB;

CREATE TABLE kiosk_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (branch_id, code),
    CONSTRAINT fk_kiosk_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
) ENGINE=InnoDB;

CREATE TABLE display_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    target_request_group_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (branch_id, code),
    CONSTRAINT fk_display_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_display_rg FOREIGN KEY (target_request_group_id) REFERENCES request_group(id)
) ENGINE=InnoDB;

CREATE TABLE priority_customer_mock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    customer_segment_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_priority_cs FOREIGN KEY (customer_segment_id) REFERENCES customer_segment(id)
) ENGINE=InnoDB;

-- AUTH SERVICE ---
DROP DATABASE IF EXISTS bank_qms_auth;
CREATE DATABASE bank_qms_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bank_qms_auth;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL, -- Soft link tới bank_qms_management.branch
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role ENUM('TELLER', 'ADMIN', 'MANAGER') NOT NULL DEFAULT 'TELLER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB;

CREATE TABLE counter_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    counter_id BIGINT NOT NULL, -- Soft link tới bank_qms_management.service_counter
    branch_id BIGINT NOT NULL,  -- Soft link
    status ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB;

--- TICKET SERVICE ---
DROP DATABASE IF EXISTS bank_qms_ticket;
CREATE DATABASE bank_qms_ticket CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bank_qms_ticket;

CREATE TABLE ticket_sequence (
    branch_id BIGINT NOT NULL, -- Soft link
    business_date DATE NOT NULL,
    prefix_code VARCHAR(5) NOT NULL,
    seq_value INT NOT NULL DEFAULT 0,
    PRIMARY KEY (branch_id, business_date, prefix_code)
) ENGINE=InnoDB;

CREATE TABLE ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL,             -- Soft link
    business_date DATE NOT NULL,
    ticket_no VARCHAR(10) NOT NULL,
    request_group_id BIGINT NOT NULL,      -- Soft link
    service_type_id BIGINT NULL,           -- Soft link
    customer_segment_id BIGINT NOT NULL,   -- Soft link
    phone_number VARCHAR(20) NULL,
    status ENUM('WAITING', 'CALLED', 'SERVING', 'DONE', 'SKIPPED_HOLD', 'SKIPPED_EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
    rejoin_count INT NOT NULL DEFAULT 0,
    skip_expire_at DATETIME NULL,
    wait_credit_seconds INT NOT NULL DEFAULT 0,
    call_attempt_count INT NOT NULL DEFAULT 0,
    current_counter_id BIGINT NULL,        -- Soft link
    last_called_at DATETIME NULL,
    serving_at DATETIME NULL,
    done_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (branch_id, business_date, ticket_no)
) ENGINE=InnoDB;

CREATE TABLE ticket_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,             -- Hard link trong cùng DB
    event_type ENUM('CREATED', 'CALLED', 'SERVING_STARTED', 'DONE', 'SKIPPED_HOLD', 'REJOINED', 'TRANSFERRED', 'CANCELLED', 'SKIPPED_EXPIRED') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by_user_id BIGINT NULL,      -- Soft link tới auth.app_user
    counter_id BIGINT NULL,                -- Soft link
    from_status ENUM('WAITING', 'CALLED', 'SERVING', 'DONE', 'SKIPPED_HOLD', 'SKIPPED_EXPIRED', 'CANCELLED') NULL,
    to_status ENUM('WAITING', 'CALLED', 'SERVING', 'DONE', 'SKIPPED_HOLD', 'SKIPPED_EXPIRED', 'CANCELLED') NULL,
    old_request_group_id BIGINT NULL,      -- Soft link
    new_request_group_id BIGINT NULL,      -- Soft link
    old_service_type_id BIGINT NULL,       -- Soft link
    new_service_type_id BIGINT NULL,       -- Soft link
    note VARCHAR(255) NULL,
    CONSTRAINT fk_event_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB;

-- Tối ưu Index cho truy vấn độc lập (Bắt buộc vì không còn Khóa ngoại)
CREATE INDEX idx_ticket_branch_status ON ticket(branch_id, status);
CREATE INDEX idx_ticket_rg_status ON ticket(request_group_id, status);
CREATE INDEX idx_ticket_cs ON ticket(customer_segment_id);

--- Insert sample data ---
--- MANAGEMENT ---
USE bank_qms_management;

-- 1. Chi nhánh (id 1 và 2)
INSERT INTO branch (id, code, name, address) VALUES
                                                 (1, 'HN01', 'Architectural Banking - Hà Nội', 'Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội'),
                                                 (2, 'HCM01', 'Architectural Banking - Hồ Chí Minh', 'Quận 1, TP.HCM');

-- 2. Phân khúc khách hàng (id 1, 2, 3)
INSERT INTO customer_segment (id, code, name, base_priority_score) VALUES
                                                                       (1, 'PERSONAL', 'Khách hàng cá nhân', 0),
                                                                       (2, 'BUSINESS', 'Khách hàng doanh nghiệp', 50);

-- 3. Nhóm Dịch vụ (Lấy ký tự Prefix để in vé)
-- 1. NHÓM DỊCH VỤ CHO KHÁCH HÀNG CÁ NHÂN (customer_segment_id = 1)
INSERT INTO request_group (id, customer_segment_id, code, prefix_code, name, description) VALUES
                                                                                              (1, 1, 'PERS_CASH', 'A', 'Tiền mặt & Tài khoản', 'Giao dịch nộp/rút tiền, chuyển khoản tại quầy'),
                                                                                              (2, 1, 'PERS_CARD', 'B', 'Dịch vụ Thẻ', 'Phát hành thẻ, xử lý sự cố thẻ, mã PIN'),
                                                                                              (3, 1, 'PERS_DIGITAL', 'D', 'Ngân hàng số', 'Đăng ký Smart Banking, thay đổi hạn mức online'),
                                                                                              (4, 1, 'PERS_SAVINGS', 'S', 'Tiết kiệm & Đầu tư', 'Mở sổ tiết kiệm, tất toán, chứng chỉ tiền gửi'),
                                                                                              (5, 1, 'PERS_LOAN', 'L', 'Vay vốn cá nhân', 'Vay tiêu dùng, mua xe, mua nhà');

-- 2. NHÓM DỊCH VỤ CHO KHÁCH HÀNG DOANH NGHIỆP (customer_segment_id = 2)
INSERT INTO request_group (id, customer_segment_id, code, prefix_code, name, description) VALUES
                                                                                              (6, 2, 'CORP_PAYMENT', 'E', 'Thanh toán & Ngân quỹ', 'Nộp/rút tiền doanh nghiệp, chi lương, nộp thuế'),
                                                                                              (7, 2, 'CORP_CREDIT', 'F', 'Tín dụng & Bảo lãnh', 'Cho vay trung dài hạn, bảo lãnh dự thầu'),
                                                                                              (8, 2, 'CORP_TRADE', 'G', 'Tài trợ thương mại', 'Dịch vụ LC, nhờ thu, thanh toán quốc tế');
-- 4. Dịch vụ chi tiết (Service Type)
-- Dịch vụ cho nhóm Tiền mặt & Tài khoản (Cá nhân)
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (1, 'PERS_DEPOSIT', 'Nộp tiền mặt vào tài khoản', 5, 0, 15),
                                                                                                                   (1, 'PERS_WITHDRAW', 'Rút tiền mặt', 7, 0, 15),
                                                                                                                   (1, 'PERS_TRANSFER', 'Chuyển tiền liên ngân hàng', 10, 0, 20),
                                                                                                                   (1, 'PERS_ACC_OPEN', 'Mở tài khoản thanh toán', 15, 5, 25);

-- Dịch vụ cho nhóm Thẻ (Cá nhân)
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (2, 'CARD_NEW', 'Phát hành thẻ mới (ATM/Credit)', 15, 10, 30),
                                                                                                                   (2, 'CARD_ISSUE', 'Xử lý thẻ nuốt/khóa thẻ', 10, 0, 20),
                                                                                                                   (2, 'CARD_PIN', 'Cấp lại mã PIN/Kích hoạt thẻ', 5, 0, 15);

-- Dịch vụ cho nhóm Ngân hàng số (Cá nhân)
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (3, 'DIGI_REG', 'Đăng ký/Kích hoạt Smart Banking', 10, 0, 20),
                                                                                                                   (3, 'DIGI_RESET', 'Quên mật khẩu/Cấp lại mã OTP', 8, 0, 15);

-- Dịch vụ cho nhóm Tiết kiệm (Cá nhân)
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (4, 'SAV_OPEN', 'Gửi tiết kiệm mới', 12, 15, 25),
                                                                                                                   (4, 'SAV_CLOSE', 'Tất toán sổ tiết kiệm', 10, 5, 20);

-- Dịch vụ cho Doanh nghiệp - Thanh toán
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (6, 'CORP_CASH_IN', 'Nộp tiền mặt doanh nghiệp (số lượng lớn)', 25, 20, 45),
                                                                                                                   (6, 'CORP_TAX', 'Nộp thuế ngân sách nhà nước', 15, 10, 30),
                                                                                                                   (6, 'CORP_SALARY', 'Giao dịch chi lương theo lô', 30, 25, 60);

-- Dịch vụ cho Doanh nghiệp - Tín dụng & Bảo lãnh
INSERT INTO service_type (request_group_id, code, name, average_service_minutes, priority_weight, sla_minutes) VALUES
                                                                                                                   (7, 'CORP_GUARANTEE', 'Phát hành bảo lãnh dự thầu/thanh toán', 45, 30, 90),
                                                                                                                   (7, 'CORP_LOAN_DISBURSE', 'Giải ngân vốn vay doanh nghiệp', 40, 30, 90);
-- 5. Quầy giao dịch (Giả lập 3 quầy tại chi nhánh HN01)
INSERT INTO service_counter (id, branch_id, code, name, status) VALUES
                                                                    (1, 1, 'Q01', 'Quầy số 01', 'AVAILABLE'),
                                                                    (2, 1, 'Q02', 'Quầy số 02', 'AVAILABLE'),
                                                                    (3, 1, 'Q03', 'Quầy Doanh nghiệp', 'AVAILABLE');

-- 6. Ánh xạ Quầy - Nhóm Dịch vụ (Quầy 1 & 2 làm Tiền mặt + Thẻ, Quầy 3 làm Tất cả)
INSERT INTO counter_request_group (counter_id, request_group_id) VALUES
                                                                     (1, 1), (1, 2), -- Quầy 1: Tiền mặt + Thẻ
                                                                     (2, 1),         -- Quầy 2: Chỉ tiền mặt
                                                                     (3, 3);
-- 7. Ánh xạ Quầy - Phân khúc Khách hàng (Routing cứng)
INSERT INTO counter_customer_segment (counter_id, customer_segment_id) VALUES
                                                                           (1, 1), -- Quầy 1 phục vụ Cá nhân
                                                                           (2, 1), -- Quầy 2 phục vụ Cá nhân
                                                                           (3, 2); -- Quầy 3 phục vụ Doanh nghiệp
-- 8. Thiết bị Kiosk và Màn hình hiển thị trung tâm tại HN01
INSERT INTO kiosk_config (branch_id, code, name) VALUES (1, 'K-HN01-01', 'Kiosk Cửa ra vào');
INSERT INTO display_config (branch_id, code, name) VALUES (1, 'D-HN01-MAIN', 'Màn hình sảnh chính');

-- 9. Dữ liệu giả lập khách hàng ưu tiên (Dùng sđt để Kiosk nhận diện VIP/Business)
INSERT INTO priority_customer_mock (phone_number, full_name, customer_segment_id) VALUES
                                                                                      ('0909000001', 'Công ty TNHH Phần Mềm', 2); -- Doanh nghiệp

USE bank_qms_management;
CREATE TABLE reason (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        code VARCHAR(50) NOT NULL UNIQUE, -- Ví dụ: WRONG_SERVICE, LONG_WAIT
                        name VARCHAR(255) NOT NULL,      -- Tên hiển thị: "Sai nghiệp vụ", "Khách không đợi được"
                        type ENUM('TRANSFER', 'CANCEL', 'HOLD', 'SKIP') NOT NULL,
                        is_active BOOLEAN DEFAULT TRUE,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

USE bank_qms_ticket;
ALTER TABLE ticket_event
    ADD COLUMN reason_id BIGINT NULL AFTER event_type;

-- Tạo index để tối ưu truy vấn báo cáo sau này
CREATE INDEX idx_event_reason ON ticket_event(reason_id);

USE bank_qms_management;
INSERT INTO reason (code, name, type) VALUES
-- Lý do chuyển quầy
('T_WRONG_SERVICE', 'Chọn sai luồng nghiệp vụ', 'TRANSFER'),
('T_ESCALATION', 'Chuyển cấp thẩm quyền phê duyệt', 'TRANSFER'),
('T_NEXT_STEP', 'Chuyển tiếp quy trình liên thông', 'TRANSFER'),
('T_DEVICE_ERROR', 'Sự cố kỹ thuật tại quầy', 'TRANSFER'),

-- Lý do hủy phiếu
('C_CUSTOMER_LEFT', 'Khách hàng đã rời đi (Vắng mặt)', 'CANCEL'),
('C_INPUT_ERROR', 'Nhập sai thông tin giao dịch', 'CANCEL'),
('C_DUPLICATE', 'Trùng lặp số thứ tự', 'CANCEL'),

-- Lý do tạm dừng/hoãn (Hold/Skip)
('H_DOC_WAITING', 'Đợi khách hàng bổ sung hồ sơ', 'HOLD'),
('S_CALL_TIMEOUT', 'Gọi quá số lần quy định - Không phản hồi', 'SKIP');