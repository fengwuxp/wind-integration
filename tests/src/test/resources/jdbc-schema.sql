-- ------------------------------------
-- 系统配置表
-- ------------------------------------
drop table if exists `t_system_config`;
create table `t_system_config`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `gmt_create`     datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `name`           varchar(100)        NOT NULL COMMENT '配置名称',
    `config_content` text                NOT NULL COMMENT '配置内容',
    `content_type`   varchar(10)         NOT NULL COMMENT '配置内容类型，TEXT\JSON',
    `config_group`   varchar(50)         NOT NULL COMMENT '配置分组',
    `is_encryption`  tinyint(3)          NOT NULL COMMENT '是否加密',
    `is_enabled`     tinyint(3)          NOT NULL COMMENT '是否启用',
    `description`    varchar(150) DEFAULT NULL COMMENT '配置描述',
    primary key (`id`),
    unique `uk_system_config_name` (`name`)
) COMMENT = '系统配置表' ENGINE = InnoDB
                         default charset = utf8mb4;