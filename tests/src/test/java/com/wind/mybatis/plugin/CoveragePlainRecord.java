package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/** 字段名称不能代替消费方对隔离维度的声明。 */
@Getter
@Setter
@Table(value = "coverage_plain_record", mapperGenerateEnable = false)
public class CoveragePlainRecord {

    @Id
    private Long id;

    private Long tenantId;

    private Long ownerId;
}
