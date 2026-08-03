CREATE TABLE `GRAPH_THREAD`
(
    `thread_id`          varchar(300) NOT NULL,
    `thread_name`        varchar(300) DEFAULT NULL,
    `is_released`        tinyint(1) NOT NULL DEFAULT '0',
    `active_thread_name` varchar(255) GENERATED ALWAYS AS ((case when (`is_released` = false) then `thread_name` else NULL end)) STORED,
    PRIMARY KEY (`thread_id`),
    UNIQUE KEY `IDX_GRAPH_THREAD_ACTIVE_NAME` (`active_thread_name`)
);


CREATE TABLE GRAPH_CHECKPOINT
(
    checkpoint_seq  BIGINT       NOT NULL AUTO_INCREMENT UNIQUE,
    checkpoint_id   VARCHAR(36) PRIMARY KEY,
    thread_id       VARCHAR(300) NOT NULL,
    node_id         VARCHAR(255),
    next_node_id    VARCHAR(255),
    state_data      JSON         NOT NULL,
    state_data_json MEDIUMTEXT,
    saved_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT GRAPH_FK_THREAD
        FOREIGN KEY (thread_id)
            REFERENCES GRAPH_THREAD (thread_id)
            ON DELETE CASCADE
) COMMENT='检查点表';



CREATE TABLE `chat_sessions`
(
    `id`           bigint                                  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      varchar(36) COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '用户ID',
    `name`         varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话名称',
    `last_message` text COLLATE utf8mb4_unicode_ci COMMENT '最后一条消息预览',
    `create_time`  datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY            `idx_user_id` (`user_id`),
    KEY            `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';


CREATE TABLE `id_generator`
(
    `id`          bigint                                   NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `code`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编码（唯一）',
    `value`       bigint                                   NOT NULL DEFAULT 1 COMMENT '当前值，每次调用 +1',
    `create_time` datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ID 生成器表';


CREATE TABLE `agent`
(
    `id`              bigint                                   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`            varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent 编码（唯一）',
    `name`            varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent 名称',
    `request_type`    varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'send' COMMENT '请求类型：send 普通 / stream 流式',
    `system_prompt`   text COLLATE utf8mb4_unicode_ci COMMENT '系统提示词（支持 Markdown）',
    `tool_code_list`  varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具编码列表，逗号分隔',
    `skill_id_list`   varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能 ID 列表，逗号分隔',
    `create_time`     datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user`     varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 表';