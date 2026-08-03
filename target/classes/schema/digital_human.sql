CREATE TABLE IF NOT EXISTS digital_human (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL COMMENT '业务编码，唯一',
    name VARCHAR(100) NOT NULL COMMENT '数字人/Agent名称',
    agent_type VARCHAR(20) NOT NULL DEFAULT 'parent' COMMENT '类型：parent-父(数字人), child-子(Agent)',
    parent_code VARCHAR(100) COMMENT '父级code，agent_type=child时必填',
    avatar VARCHAR(500) COMMENT '头像URL',
    description VARCHAR(500) COMMENT '描述',
    system_prompt TEXT COMMENT '系统提示词',
    multi_agent_enabled TINYINT DEFAULT 0 COMMENT '多Agent开关 0-关闭 1-开启',
    streaming_enabled TINYINT DEFAULT 0 COMMENT '流式交互开关 0-关闭 1-开启',
    skill_ids TEXT COMMENT '关联技能ID列表(JSON格式)',
    tool_ids TEXT COMMENT '关联工具ID列表(JSON格式)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_user VARCHAR(100) COMMENT '更新人',
    UNIQUE KEY uk_code (code),
    INDEX idx_name (name),
    INDEX idx_agent_type (agent_type),
    INDEX idx_parent_code (parent_code),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字人/Agent统一表';

ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS code VARCHAR(100) COMMENT '业务编码';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS agent_type VARCHAR(20) DEFAULT 'parent' COMMENT '类型：parent-父, child-子';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS parent_code VARCHAR(100) COMMENT '父级code';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS system_prompt TEXT COMMENT '系统提示词';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS multi_agent_enabled TINYINT DEFAULT 0 COMMENT '多Agent开关';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS streaming_enabled TINYINT DEFAULT 0 COMMENT '流式交互开关';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS skill_ids TEXT COMMENT '关联技能ID列表(JSON格式)';
ALTER TABLE digital_human ADD COLUMN IF NOT EXISTS tool_ids TEXT COMMENT '关联工具ID列表(JSON格式)';

CREATE TABLE IF NOT EXISTS skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '技能名称',
    category VARCHAR(50) COMMENT '分类',
    version VARCHAR(20) DEFAULT 'v1.0.0' COMMENT '版本号',
    content TEXT COMMENT '技能内容',
    description VARCHAR(500) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_user VARCHAR(100) COMMENT '更新人',
    UNIQUE KEY uk_name (name),
    INDEX idx_category (category),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能表';

CREATE TABLE IF NOT EXISTS tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '工具名称',
    type VARCHAR(50) COMMENT '工具类型',
    config TEXT COMMENT '配置参数(JSON), type=rpc时存储接口Schema(interfaceName,methodName,params入参,responseParams出参等)',
    permission VARCHAR(20) DEFAULT 'public' COMMENT '权限级别 public/private',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_user VARCHAR(100) COMMENT '更新人',
    UNIQUE KEY uk_name (name),
    INDEX idx_type (type),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具表';

-- Agent 已合并到 digital_human 表，通过 agent_type 区分 parent/child

INSERT INTO tool (name, type, config, permission, description, create_time, update_time, update_user) VALUES
('web_fetch', 'system', NULL, 'public', '网络搜索工具，当需要从网络搜索相关信息时使用', NOW(), NOW(), 'system'),
('write_file', 'system', NULL, 'public', '文件写入工具，当需要写入或保存文件时使用', NOW(), NOW(), 'system'),
('execute_sql', 'system', NULL, 'public', 'SQL执行工具，当需要查询数据库时使用', NOW(), NOW(), 'system'),
('shell_tool', 'system', NULL, 'public', 'Shell命令执行工具，当需要执行系统命令时使用', NOW(), NOW(), 'system'),
('query_order_detail', 'rpc', '{"interfaceName":"com.example.order.OrderService","methodName":"queryOrderDetail","group":"order-group","version":"1.0.0","timeout":5000,"params":[{"name":"orderId","type":"String","description":"订单ID","required":true,"example":"10001"},{"name":"orderNo","type":"String","description":"订单编号","required":false,"example":"ORD10001"}],"responseParams":[{"name":"orderId","type":"String","description":"订单ID","example":"10001"},{"name":"orderNo","type":"String","description":"订单编号","example":"ORD10001"},{"name":"status","type":"String","description":"订单状态","example":"PAID"},{"name":"statusDesc","type":"String","description":"状态描述","example":"已支付"},{"name":"amount","type":"BigDecimal","description":"订单金额","example":"299.99"},{"name":"createTime","type":"String","description":"创建时间/下单时间","example":"2026-07-28 10:30:00"},{"name":"payTime","type":"String","description":"支付时间","example":"2026-07-28 10:32:00"},{"name":"shippingAddress","type":"Object","description":"收货地址信息","example":"{...}"},{"name":"items","type":"Array","description":"商品明细列表","example":"[...]"},{"name":"remark","type":"String","description":"备注","example":"请尽快发货"}]}', 'public', '查询订单详情', NOW(), NOW(), 'system'),
('query_user_info', 'rpc', '{"interfaceName":"com.example.user.UserService","methodName":"queryUserInfo","group":"user-group","version":"1.0.0","timeout":3000,"params":[{"name":"userId","type":"String","description":"用户ID","required":true,"example":"20001"}],"responseParams":[{"name":"userId","type":"String","description":"用户ID","example":"20001"},{"name":"userName","type":"String","description":"用户名","example":"用户20001"},{"name":"nickName","type":"String","description":"昵称","example":"昵称20001"},{"name":"mobile","type":"String","description":"手机号","example":"138****8888"},{"name":"email","type":"String","description":"邮箱","example":"user@example.com"},{"name":"status","type":"String","description":"账户状态","example":"ACTIVE"},{"name":"statusDesc","type":"String","description":"状态描述","example":"正常"},{"name":"level","type":"String","description":"用户等级","example":"VIP"},{"name":"registerTime","type":"String","description":"注册时间","example":"2025-01-15 09:00:00"},{"name":"lastLoginTime","type":"String","description":"最近登录时间","example":"2026-07-28 08:30:00"}]}', 'public', '查询用户信息', NOW(), NOW(), 'system'),
('query_product_detail', 'rpc', '{"interfaceName":"com.example.product.ProductService","methodName":"queryProductDetail","group":"product-group","version":"1.0.0","timeout":3000,"params":[{"name":"productId","type":"String","description":"商品ID","required":true,"example":"30001"},{"name":"skuId","type":"String","description":"SKU ID","required":false,"example":"SKU30001"}],"responseParams":[{"name":"productId","type":"String","description":"商品ID","example":"30001"},{"name":"skuId","type":"String","description":"SKU ID","example":"SKU30001"},{"name":"name","type":"String","description":"商品名称","example":"商品30001"},{"name":"category","type":"String","description":"商品分类","example":"电子产品"},{"name":"brand","type":"String","description":"品牌","example":"品牌A"},{"name":"price","type":"BigDecimal","description":"售价","example":"199.99"},{"name":"originalPrice","type":"BigDecimal","description":"原价","example":"299.99"},{"name":"stock","type":"Integer","description":"库存","example":"100"},{"name":"sold","type":"Integer","description":"已售数量","example":"520"},{"name":"description","type":"String","description":"商品描述","example":"这是一款优质商品"},{"name":"status","type":"String","description":"商品状态","example":"ON_SALE"},{"name":"statusDesc","type":"String","description":"状态描述","example":"在售"}]}', 'public', '查询商品详情', NOW(), NOW(), 'system');