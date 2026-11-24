-- 非破坏性迁移：升级前备份数据库；在停止旧后端后执行。
USE girl_shop;
CREATE TABLE IF NOT EXISTS tb_order_hidden (
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (order_id, user_id)
);
