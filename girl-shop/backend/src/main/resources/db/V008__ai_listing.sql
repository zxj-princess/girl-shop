CREATE TABLE IF NOT EXISTS tb_ai_listing_job (
 id VARCHAR(36) PRIMARY KEY,user_id BIGINT NOT NULL,request_id VARCHAR(64) NOT NULL,input_hash VARCHAR(64) NOT NULL,
 input_payload TEXT NOT NULL,draft_id BIGINT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',error_message VARCHAR(200) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_ai_request(user_id,request_id),KEY idx_ai_queue(status,created_at),KEY idx_ai_user(user_id,created_at)
);
CREATE TABLE IF NOT EXISTS tb_draft_publication (
 draft_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,product_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
