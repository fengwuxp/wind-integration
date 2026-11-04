package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import lombok.Getter;
import lombok.Setter;

/** UNION 的另一个分支同时具备租户和工作空间边界，并使用不同物理列名。 */
@Getter
@Setter
@Table(value = "composite_workspace_record", mapperGenerateEnable = false)
public class CompositeWorkspaceRecord implements TenantIsolationObject<Long>, WorkspaceIsolationObject<Long> {

    @Id
    private Long id;

    @Column("account_key")
    private Long tenantId;

    @Column("workspace_key")
    private Long workspaceId;

}
