-- ============================================
-- Yumi 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS yumi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE yumi;

-- ============================================
-- 1. 会话相关表
-- ============================================

-- 会话表
CREATE TABLE IF NOT EXISTS `chat_sessions`
(
    `id`               bigint                                  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`          varchar(36) COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '用户ID',
    `name`             varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话名称',
    `digital_human_id` bigint                                  DEFAULT NULL COMMENT '数字人ID',
    `last_message`     text COLLATE utf8mb4_unicode_ci COMMENT '最后一条消息预览',
    `create_time`      datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                `idx_user_id` (`user_id`),
    KEY                `idx_update_time` (`update_time`),
    KEY                `idx_digital_human_id` (`digital_human_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ============================================
-- 2. Agent 图执行相关表（Spring AI Alibaba Graph）
-- ============================================

-- 线程表
CREATE TABLE IF NOT EXISTS `GRAPH_THREAD`
(
    `thread_id`          varchar(300) NOT NULL,
    `thread_name`        varchar(300) DEFAULT NULL,
    `is_released`        tinyint(1)   NOT NULL DEFAULT '0',
    `active_thread_name` varchar(255) GENERATED ALWAYS AS ((case when (`is_released` = false) then `thread_name` else NULL end)) STORED,
    PRIMARY KEY (`thread_id`),
    UNIQUE KEY `IDX_GRAPH_THREAD_ACTIVE_NAME` (`active_thread_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 图执行线程表';

-- 检查点表
CREATE TABLE IF NOT EXISTS `GRAPH_CHECKPOINT`
(
    `checkpoint_seq`  BIGINT       NOT NULL AUTO_INCREMENT UNIQUE,
    `checkpoint_id`   VARCHAR(36)  NOT NULL,
    `thread_id`       VARCHAR(300) NOT NULL,
    `node_id`         VARCHAR(255) DEFAULT NULL,
    `next_node_id`    VARCHAR(255) DEFAULT NULL,
    `state_data`      JSON         NOT NULL,
    `state_data_json` MEDIUMTEXT,
    `saved_at`        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`checkpoint_id`),
    CONSTRAINT `GRAPH_FK_THREAD`
        FOREIGN KEY (`thread_id`)
            REFERENCES `GRAPH_THREAD` (`thread_id`)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 图执行检查点表';

-- ============================================
-- 3. 数字人/Agent 管理表
-- ============================================

-- 数字人/Agent 统一表
CREATE TABLE IF NOT EXISTS `digital_human`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `code`                VARCHAR(100) NOT NULL COMMENT '业务编码，唯一',
    `name`                VARCHAR(100) NOT NULL COMMENT '数字人/Agent名称',
    `agent_type`          VARCHAR(20)  NOT NULL DEFAULT 'parent' COMMENT '类型：parent-父(数字人), child-子(Agent)',
    `parent_code`         VARCHAR(100)          DEFAULT NULL COMMENT '父级code，agent_type=child时必填',
    `avatar`              VARCHAR(500)          DEFAULT NULL COMMENT '头像URL',
    `description`         VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `system_prompt`       TEXT                  DEFAULT NULL COMMENT '系统提示词',
    `multi_agent_enabled` TINYINT               DEFAULT 0 COMMENT '多Agent开关 0-关闭 1-开启',
    `streaming_enabled`   TINYINT               DEFAULT 0 COMMENT '流式交互开关 0-关闭 1-开启',
    `skill_ids`           TEXT                  DEFAULT NULL COMMENT '关联技能ID列表(JSON格式)',
    `tool_ids`            TEXT                  DEFAULT NULL COMMENT '关联工具ID列表(JSON格式)',
    `config`              TEXT                  DEFAULT NULL COMMENT '扩展配置(JSON格式)',
    `create_time`         DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user`         VARCHAR(100)          DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_name` (`name`),
    KEY `idx_agent_type` (`agent_type`),
    KEY `idx_parent_code` (`parent_code`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字人/Agent统一表';

-- ============================================
-- 4. 技能管理表
-- ============================================

-- 技能表
CREATE TABLE IF NOT EXISTS `skill`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL COMMENT '技能名称',
    `category`    VARCHAR(50)           DEFAULT NULL COMMENT '分类',
    `version`     VARCHAR(20)           DEFAULT 'v1.0.0' COMMENT '版本号',
    `content`     TEXT                  DEFAULT NULL COMMENT '技能内容(Markdown格式)',
    `description` VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `status`      TINYINT               DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user` VARCHAR(100)          DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_category` (`category`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能表';

-- ============================================
-- 5. 工具管理表
-- ============================================

-- 工具表
CREATE TABLE IF NOT EXISTS `tool`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL COMMENT '工具名称',
    `type`        VARCHAR(50)           DEFAULT NULL COMMENT '工具类型(system/rpc等)',
    `config`      TEXT                  DEFAULT NULL COMMENT '配置参数(JSON), type=rpc时存储接口Schema',
    `permission`  VARCHAR(20)           DEFAULT 'public' COMMENT '权限级别 public/private',
    `description` VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user` VARCHAR(100)          DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_type` (`type`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具表';

-- ============================================
-- 6. RPC 接口管理表
-- ============================================

-- RPC 接口表
CREATE TABLE IF NOT EXISTS `rpc_interface`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(100) NOT NULL COMMENT '接口名称',
    `description`    VARCHAR(500)          DEFAULT NULL COMMENT '接口描述',
    `interface_name` VARCHAR(200) NOT NULL COMMENT '接口全限定名',
    `method_name`    VARCHAR(100) NOT NULL COMMENT '方法名',
    `params_json`    TEXT                  DEFAULT NULL COMMENT '参数定义(JSON)',
    `group_name`     VARCHAR(100)          DEFAULT NULL COMMENT 'RPC 分组名',
    `version`        VARCHAR(20)           DEFAULT '1.0.0' COMMENT '接口版本',
    `timeout`        INT                   DEFAULT 5000 COMMENT '超时时间(毫秒)',
    `enabled`        TINYINT               DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `create_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user`    VARCHAR(100)          DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interface_method` (`interface_name`, `method_name`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RPC 接口表';

-- ============================================
-- 7. ID 生成器表
-- ============================================

-- ID 生成器表
CREATE TABLE IF NOT EXISTS `id_generator`
(
    `id`          bigint                                   NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `code`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编码（唯一）',
    `value`       bigint                                   NOT NULL DEFAULT 1 COMMENT '当前值，每次调用 +1',
    `create_time` datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ID 生成器表';

-- ============================================
-- 8. 初始化数据
-- ============================================

-- 插入系统工具
INSERT INTO `tool` (`name`, `type`, `config`, `permission`, `description`, `create_time`, `update_time`, `update_user`) VALUES
('web_fetch', 'system', NULL, 'public', '网络搜索工具，当需要从网络搜索相关信息时使用', NOW(), NOW(), 'system'),
('write_file', 'system', NULL, 'public', '文件写入工具，当需要写入或保存文件时使用', NOW(), NOW(), 'system'),
('execute_sql', 'system', NULL, 'public', 'SQL执行工具，当需要查询数据库时使用', NOW(), NOW(), 'system'),
('shell_tool', 'system', NULL, 'public', 'Shell命令执行工具，当需要执行系统命令时使用', NOW(), NOW(), 'system'),
('query_order_detail', 'rpc', '{"interfaceName":"com.example.order.OrderService","methodName":"queryOrderDetail","group":"order-group","version":"1.0.0","timeout":5000,"params":[{"name":"orderId","type":"String","description":"订单ID","required":true,"example":"10001"},{"name":"orderNo","type":"String","description":"订单编号","required":false,"example":"ORD10001"}],"responseParams":[{"name":"orderId","type":"String","description":"订单ID","example":"10001"},{"name":"orderNo","type":"String","description":"订单编号","example":"ORD10001"},{"name":"status","type":"String","description":"订单状态","example":"PAID"},{"name":"statusDesc","type":"String","description":"状态描述","example":"已支付"},{"name":"amount","type":"BigDecimal","description":"订单金额","example":"299.99"},{"name":"createTime","type":"String","description":"创建时间/下单时间","example":"2026-07-28 10:30:00"},{"name":"payTime","type":"String","description":"支付时间","example":"2026-07-28 10:32:00"},{"name":"shippingAddress","type":"Object","description":"收货地址信息","example":"{...}"},{"name":"items","type":"Array","description":"商品明细列表","example":"[...]"},{"name":"remark","type":"String","description":"备注","example":"请尽快发货"}]}', 'public', '查询订单详情', NOW(), NOW(), 'system'),
('query_user_info', 'rpc', '{"interfaceName":"com.example.user.UserService","methodName":"queryUserInfo","group":"user-group","version":"1.0.0","timeout":3000,"params":[{"name":"userId","type":"String","description":"用户ID","required":true,"example":"20001"}],"responseParams":[{"name":"userId","type":"String","description":"用户ID","example":"20001"},{"name":"userName","type":"String","description":"用户名","example":"用户20001"},{"name":"nickName","type":"String","description":"昵称","example":"昵称20001"},{"name":"mobile","type":"String","description":"手机号","example":"138****8888"},{"name":"email","type":"String","description":"邮箱","example":"user@example.com"},{"name":"status","type":"String","description":"账户状态","example":"ACTIVE"},{"name":"statusDesc","type":"String","description":"状态描述","example":"正常"},{"name":"level","type":"String","description":"用户等级","example":"VIP"},{"name":"registerTime","type":"String","description":"注册时间","example":"2025-01-15 09:00:00"},{"name":"lastLoginTime","type":"String","description":"最近登录时间","example":"2026-07-28 08:30:00"}]}', 'public', '查询用户信息', NOW(), NOW(), 'system'),
('query_product_detail', 'rpc', '{"interfaceName":"com.example.product.ProductService","methodName":"queryProductDetail","group":"product-group","version":"1.0.0","timeout":3000,"params":[{"name":"productId","type":"String","description":"商品ID","required":true,"example":"30001"},{"name":"skuId","type":"String","description":"SKU ID","required":false,"example":"SKU30001"}],"responseParams":[{"name":"productId","type":"String","description":"商品ID","example":"30001"},{"name":"skuId","type":"String","description":"SKU ID","example":"SKU30001"},{"name":"name","type":"String","description":"商品名称","example":"商品30001"},{"name":"category","type":"String","description":"商品分类","example":"电子产品"},{"name":"brand","type":"String","description":"品牌","example":"品牌A"},{"name":"price","type":"BigDecimal","description":"售价","example":"199.99"},{"name":"originalPrice","type":"BigDecimal","description":"原价","example":"299.99"},{"name":"stock","type":"Integer","description":"库存","example":"100"},{"name":"sold","type":"Integer","description":"已售数量","example":"520"},{"name":"description","type":"String","description":"商品描述","example":"这是一款优质商品"},{"name":"status","type":"String","description":"商品状态","example":"ON_SALE"},{"name":"statusDesc","type":"String","description":"状态描述","example":"在售"}]}', 'public', '查询商品详情', NOW(), NOW(), 'system');

-- 插入示例数字人
INSERT INTO `digital_human` (`code`, `name`, `agent_type`, `parent_code`, `avatar`, `description`, `system_prompt`, `multi_agent_enabled`, `streaming_enabled`, `skill_ids`, `tool_ids`, `create_time`, `update_time`, `update_user`) VALUES
('yumi', 'Yumi', 'parent', NULL, NULL, 'Yumi 智能助手', '你是一个智能助手 Yumi，可以帮助用户解答问题、执行任务。', 0, 1, NULL, '1,2,3,4', NOW(), NOW(), 'system');

-- 插入 ID 生成器初始数据
INSERT INTO `id_generator` (`code`, `value`, `create_time`, `update_time`) VALUES
('session', 1, NOW(), NOW()),
('checkpoint', 1, NOW(), NOW());

-- ============================================
-- 初始化完成
-- ============================================