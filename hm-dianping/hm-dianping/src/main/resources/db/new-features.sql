-- =============================================================
-- 二手服装交易平台 新增功能建表脚本
-- 包含：关注卖家 / 买家秀 / 卖家评价 / 上新消息通知
-- 数据库: girl_shop（需先执行 girl-shop.sql 初始化基础表与商品/订单数据）
-- =============================================================
USE `girl_shop`;

-- 清理旧表（注意外键无依赖，按依赖反序删除）
DROP TABLE IF EXISTS `tb_user_notice`;
DROP TABLE IF EXISTS `tb_buyer_show`;
DROP TABLE IF EXISTS `tb_seller_comment`;
DROP TABLE IF EXISTS `tb_follow`;

-- -----------------------------------------------
-- 关注表：买家关注卖家
-- 唯一索引防止重复关注；应用层禁止自关注
-- -----------------------------------------------
CREATE TABLE `tb_follow` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT      NOT NULL COMMENT '关注者id（买家）',
  `follow_user_id` BIGINT      NOT NULL COMMENT '被关注者id（卖家）',
  `create_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
  KEY `idx_follow_user` (`follow_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注表';

-- -----------------------------------------------
-- 买家秀表：交易完成后买家晒单
-- 一个订单只能发布一次买家秀（唯一索引约束）
-- -----------------------------------------------
CREATE TABLE `tb_buyer_show` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`    BIGINT        NOT NULL COMMENT '订单id',
  `product_id`  BIGINT        NOT NULL COMMENT '商品id',
  `user_id`     BIGINT        NOT NULL COMMENT '买家id（发布人）',
  `seller_id`   BIGINT        NOT NULL COMMENT '卖家id',
  `images`      VARCHAR(1000) DEFAULT '' COMMENT '买家秀图片，逗号分隔',
  `content`     TEXT          COMMENT '买家秀文字描述',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买家秀表';

-- -----------------------------------------------
-- 卖家评价表：订单完成后买家评价卖家
-- 一个订单只能评价一次卖家（唯一索引约束）
-- -----------------------------------------------
CREATE TABLE `tb_seller_comment` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`    BIGINT        NOT NULL COMMENT '订单id',
  `seller_id`   BIGINT        NOT NULL COMMENT '被评价的卖家id',
  `user_id`     BIGINT        NOT NULL COMMENT '评价人id（买家）',
  `score`       INT           NOT NULL COMMENT '星级评分 1~5',
  `content`     TEXT          COMMENT '评价内容',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_seller` (`seller_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '卖家评价表';

-- -----------------------------------------------
-- 消息通知表：卖家上新异步推送给粉丝
-- -----------------------------------------------
CREATE TABLE `tb_user_notice` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT        NOT NULL COMMENT '接收通知的粉丝id',
  `seller_id`   BIGINT        NOT NULL COMMENT '上新卖家id',
  `product_id`  BIGINT        NOT NULL COMMENT '新商品id',
  `title`       VARCHAR(100)  DEFAULT '' COMMENT '商品标题快照',
  `product_image` VARCHAR(512) DEFAULT '' COMMENT '商品首图快照',
  `is_read`     TINYINT(1)    DEFAULT 0 COMMENT '是否已读 0未读 1已读',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_seller` (`seller_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息通知表';

-- =============================================================
-- 演示数据（与现有商品/订单数据匹配，图片内容与商品类目一致）
-- 假设：用户1=衣橱小主(卖家13800000001)，用户2/3=买家
-- 假设：商品1~24为各类服装，订单已完成(status=3)
-- =============================================================

-- ---------- 关注演示数据 ----------
-- 买家2、3 关注 卖家1（衣橱小主）
INSERT INTO `tb_follow` (`user_id`, `follow_user_id`) VALUES
(2, 1), (3, 1);

-- ---------- 买家秀演示数据（图片与商品内容匹配） ----------
-- 商品1=外套 → 买家秀写外套穿搭；商品5=连衣裙 → 写连衣裙；商品9=鞋子 → 写鞋子
INSERT INTO `tb_buyer_show` (`order_id`, `product_id`, `user_id`, `seller_id`, `images`, `content`) VALUES
(1001, 1, 2, 1, 'imgs/product1.jpg', '这件外套收到啦，版型很正，秋冬搭配毛衣刚刚好，成色和描述一致，衣橱小主发货也快～'),
(1002, 5, 3, 1, 'imgs/product5.jpg', '连衣裙上身效果绝了，面料柔软不扎，粉色超级温柔，姐妹们冲！'),
(1003, 9, 2, 1, 'imgs/product9.jpg', '鞋子走路很舒服，鞋底磨损很少，九成新名副其实，感谢卖家～');

-- ---------- 卖家评价演示数据 ----------
INSERT INTO `tb_seller_comment` (`order_id`, `seller_id`, `user_id`, `score`, `content`) VALUES
(1001, 1, 2, 5, '卖家态度超好，回复及时，商品成色描述准确，推荐！'),
(1002, 1, 3, 5, '衣橱小主的衣服都很精致，包装也很用心，会回购的'),
(1003, 1, 2, 4, '整体满意，发货稍慢了一天但商品质量没得说');

-- ---------- 消息通知演示数据 ----------
-- 卖家1上新，通知粉丝2、3
INSERT INTO `tb_user_notice` (`user_id`, `seller_id`, `product_id`, `title`, `product_image`, `is_read`) VALUES
(2, 1, 20, '九成新粉色针织开衫', 'imgs/product20.jpg', 0),
(3, 1, 20, '九成新粉色针织开衫', 'imgs/product20.jpg', 0),
(2, 1, 21, '全新小白鞋', 'imgs/product21.jpg', 1);
