产品介绍
英文名称：Yumi
中文名称：优秘

断点常用类：
ZhiPuAiChatModel


CREATE TABLE `GRAPH_CHECKPOINT` (
`checkpoint_seq` bigint NOT NULL AUTO_INCREMENT,
`checkpoint_id` varchar(36) NOT NULL,
`thread_id` varchar(300) NOT NULL,
`node_id` varchar(255) DEFAULT NULL,
`next_node_id` varchar(255) DEFAULT NULL,
`state_data` json NOT NULL,
`state_data_json` json NOT NULL,
`saved_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`checkpoint_id`),
UNIQUE KEY `checkpoint_seq` (`checkpoint_seq`),
KEY `IDX_GRAPH_CHECKPOINT_THREAD_SEQUENCE` (`thread_id`,`checkpoint_seq`),
CONSTRAINT `GRAPH_FK_THREAD` FOREIGN KEY (`thread_id`) REFERENCES `GRAPH_THREAD` (`thread_id`) ON DELETE CASCADE
)  


CREATE TABLE `GRAPH_THREAD` (
`thread_id` varchar(300) NOT NULL,
`thread_name` varchar(300) DEFAULT NULL,
`is_released` tinyint(1) NOT NULL DEFAULT '0',
`active_thread_name` varchar(255) GENERATED ALWAYS AS ((case when (`is_released` = false) then `thread_name` else NULL end)) STORED,
PRIMARY KEY (`thread_id`),
UNIQUE KEY `IDX_GRAPH_THREAD_ACTIVE_NAME` (`active_thread_name`)
)  
