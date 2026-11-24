CREATE TABLE IF NOT EXISTS tb_flash_request (
 id BIGINT NOT NULL PRIMARY KEY,
 product_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_json TEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 publish_attempts INT NOT NULL DEFAULT 0,
 error_message VARCHAR(255),
 created_at DATETIME NOT NULL,
 deadline_at DATETIME NOT NULL,
 next_publish_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 KEY ix_flash_pending(status,next_publish_at),
 KEY ix_flash_user(product_id,user_id,status)
);
CREATE TABLE IF NOT EXISTS tb_upload_asset (
 url VARCHAR(512) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'STAGED',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
