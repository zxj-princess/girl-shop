-- =============================================================
-- 二手服装交易平台 数据库初始化脚本
-- 数据库: girl_shop (需先创建 CREATE DATABASE girl_shop DEFAULT CHARSET utf8mb4)
-- =============================================================
CREATE DATABASE IF NOT EXISTS `girl_shop` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `girl_shop`;

-- 清理旧表（可选，重新初始化时使用）
DROP TABLE IF EXISTS `tb_review`;
DROP TABLE IF EXISTS `tb_message`;
DROP TABLE IF EXISTS `tb_favorite`;
DROP TABLE IF EXISTS `tb_order`;
DROP TABLE IF EXISTS `tb_flash_sale`;
DROP TABLE IF EXISTS `tb_address`;
DROP TABLE IF EXISTS `tb_product`;
DROP TABLE IF EXISTS `tb_category`;
DROP TABLE IF EXISTS `tb_user_info`;
DROP TABLE IF EXISTS `tb_user`;

-- -----------------------------------------------
-- 用户表
-- -----------------------------------------------
CREATE TABLE `tb_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone`       VARCHAR(11)  NOT NULL COMMENT '手机号码',
  `password`    VARCHAR(128) DEFAULT NULL COMMENT '密码（短信登录可空）',
  `nick_name`   VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
  `icon`        VARCHAR(512) DEFAULT '' COMMENT '头像地址',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -----------------------------------------------
-- 用户详情表
-- -----------------------------------------------
CREATE TABLE `tb_user_info` (
  `user_id`     BIGINT       NOT NULL COMMENT '主键，用户id',
  `city`        VARCHAR(64)  DEFAULT NULL COMMENT '城市',
  `introduce`   VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `gender`      TINYINT(1)   DEFAULT NULL COMMENT '性别 0男 1女',
  `birthday`    DATE         DEFAULT NULL COMMENT '生日',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户详情表';

-- -----------------------------------------------
-- 商品分类表
-- -----------------------------------------------
CREATE TABLE `tb_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(32)  NOT NULL COMMENT '分类名称',
  `icon`        VARCHAR(512) DEFAULT '' COMMENT '分类图标',
  `sort`        INT          DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类表';

-- -----------------------------------------------
-- 收货地址表
-- -----------------------------------------------
CREATE TABLE `tb_address` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT      NOT NULL COMMENT '用户id',
  `receiver_name`  VARCHAR(32) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` VARCHAR(11) NOT NULL COMMENT '收货人手机号',
  `province`       VARCHAR(32) DEFAULT NULL COMMENT '省',
  `city`           VARCHAR(32) DEFAULT NULL COMMENT '市',
  `district`       VARCHAR(32) DEFAULT NULL COMMENT '区/县',
  `detail`         VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  `is_default`     TINYINT(1)  DEFAULT 0 COMMENT '是否默认 0否 1是',
  `create_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址表';

-- -----------------------------------------------
-- 商品表
-- -----------------------------------------------
CREATE TABLE `tb_product` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          BIGINT        NOT NULL COMMENT '发布人（卖家）id',
  `category_id`      BIGINT        NOT NULL COMMENT '分类id',
  `title`            VARCHAR(100)  NOT NULL COMMENT '标题',
  `description`      TEXT          COMMENT '描述',
  `brand`            VARCHAR(64)   DEFAULT NULL COMMENT '品牌',
  `size`             VARCHAR(20)   DEFAULT NULL COMMENT '尺码 S/M/L/XL/XXL/均码',
  `condition`        TINYINT       DEFAULT 1 COMMENT '成色 1全新 2九成新 3八成新 4七成新及以下',
  `price`            DECIMAL(10,2) NOT NULL COMMENT '售价',
  `original_price`   DECIMAL(10,2) DEFAULT NULL COMMENT '原价',
  `images`           VARCHAR(1000) DEFAULT '' COMMENT '多图片，逗号分隔',
  `status`           TINYINT       DEFAULT 1 COMMENT '状态 0下架 1在售 2已售出',
  `is_flash`         TINYINT       DEFAULT 0 COMMENT '是否捡漏 0否 1是',
  `flash_price`      DECIMAL(10,2) DEFAULT NULL COMMENT '捡漏价',
  `flash_begin_time` DATETIME      DEFAULT NULL COMMENT '捡漏开始时间',
  `flash_end_time`   DATETIME      DEFAULT NULL COMMENT '捡漏结束时间',
  `stock`            INT           DEFAULT 1 COMMENT '库存（捡漏库存）',
  `sold`             INT           DEFAULT 0 COMMENT '销量',
  `views`            INT           DEFAULT 0 COMMENT '浏览量',
  `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_flash` (`is_flash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '二手服装商品表';

-- -----------------------------------------------
-- 捡漏库存表（与商品一对一，仅捡漏商品存redis预扣数据外仍需落库备份）
-- -----------------------------------------------
CREATE TABLE `tb_flash_sale` (
  `id`          BIGINT  NOT NULL COMMENT '商品id（主键，与tb_product一致）',
  `stock`       INT     NOT NULL DEFAULT 0 COMMENT '捡漏库存',
  `begin_time`  DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time`    DATETIME DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '捡漏库存表';

-- -----------------------------------------------
-- 订单表（单表+快照，支持买家/卖家双端视图）
-- -----------------------------------------------
CREATE TABLE `tb_order` (
  `id`              BIGINT        NOT NULL COMMENT '主键（全局id）',
  `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单编号',
  `buyer_id`        BIGINT        NOT NULL COMMENT '买家id',
  `seller_id`       BIGINT        NOT NULL COMMENT '卖家id',
  `product_id`      BIGINT        NOT NULL COMMENT '商品id',
  `product_title`   VARCHAR(100)  DEFAULT NULL COMMENT '商品标题快照',
  `product_image`   VARCHAR(512)  DEFAULT NULL COMMENT '商品首图快照',
  `price`           DECIMAL(10,2) NOT NULL COMMENT '成交单价',
  `pay_amount`      DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  `receiver_name`   VARCHAR(32)   DEFAULT NULL COMMENT '收货人姓名快照',
  `receiver_phone`  VARCHAR(11)   DEFAULT NULL COMMENT '收货人手机号快照',
  `receiver_address` VARCHAR(255) DEFAULT NULL COMMENT '收货地址快照',
  `status`          TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0待付款 1待发货 2待收货 3已完成 4已关闭',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time`        DATETIME      DEFAULT NULL COMMENT '支付时间',
  `ship_time`       DATETIME      DEFAULT NULL COMMENT '发货时间',
  `receive_time`    DATETIME      DEFAULT NULL COMMENT '收货时间',
  `finish_time`     DATETIME      DEFAULT NULL COMMENT '完成时间',
  `close_time`      DATETIME      DEFAULT NULL COMMENT '关闭时间',
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer` (`buyer_id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_product` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';

-- -----------------------------------------------
-- 收藏表
-- -----------------------------------------------
CREATE TABLE `tb_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT   NOT NULL COMMENT '用户id',
  `product_id`  BIGINT   NOT NULL COMMENT '商品id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏表';

-- -----------------------------------------------
-- 留言咨询表
-- -----------------------------------------------
CREATE TABLE `tb_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`  BIGINT       NOT NULL COMMENT '商品id',
  `user_id`     BIGINT       NOT NULL COMMENT '留言用户id',
  `content`     VARCHAR(500) NOT NULL COMMENT '留言内容',
  `reply`       VARCHAR(500) DEFAULT NULL COMMENT '卖家回复',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '留言时间',
  `reply_time`  DATETIME     DEFAULT NULL COMMENT '回复时间',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品留言咨询表';

-- -----------------------------------------------
-- 评价表
-- -----------------------------------------------
CREATE TABLE `tb_review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`    BIGINT       NOT NULL COMMENT '订单id',
  `product_id`  BIGINT       NOT NULL COMMENT '商品id',
  `user_id`     BIGINT       NOT NULL COMMENT '评价人id（买家）',
  `score`       TINYINT      NOT NULL COMMENT '评分 1~5',
  `content`     VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品评价表';

-- =============================================================
-- 初始数据
-- =============================================================
INSERT INTO `tb_category` (`id`, `name`, `icon`, `sort`) VALUES
  (1, '上衣', '', 1),
  (2, '外套', '', 2),
  (3, '裤装', '', 3),
  (4, '裙装', '', 4),
  (5, '鞋靴', '', 5),
  (6, '配饰', '', 6);