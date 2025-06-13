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
) ENGINE = InnoDB
  default charset = utf8mb4 COMMENT = '系统配置表';

-- ----------------------------
-- 字典表
-- ----------------------------
DROP TABLE IF EXISTS `t_dictionary_metadata`;
CREATE TABLE `t_dictionary_metadata`
(
    `id`               bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `gmt_create`       DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP COMMENT '修改时间',
    `name`             varchar(100)        NOT NULL COMMENT '字典名称',
    `locale`           tinyint(3)          NOT NULL COMMENT '语言类型：zh-CN 中文,英文：en-US',
    `tenant_id`        bigint(20)          NOT NULL DEFAULT 0 COMMENT '归属租户',
    `dictionary_group` varchar(30)         NOT NULL COMMENT '字典所属分组',
    `business_group`   varchar(30)         NOT NULL COMMENT '业务分组',
    `description`      varchar(100)        NOT NULL COMMENT '字典描述',
    `content_type`     tinyint(3)          NOT NULL COMMENT '字典内容类型 1:json 2:text',
    `content`          text COMMENT '字典内容',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_name_locale_tenant` (`name`, `locale`, `tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '字典表';