package io.hashmatrix.data.app.datasource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据源持久化仓库。
 *
 * <p><b>多租户（D9）</b>：所有读取一律经带 {@code tenantId} 的派生查询，强制按租户过滤——
 * 严禁用裸 {@link #findById(Object)} 跨租户取数据源（WP3/WP4 据此守隔离不变量）。
 */
public interface DataSourceRepository extends JpaRepository<DataSourceEntity, UUID> {

    /** 列出本租户的全部数据源（D9）。 */
    List<DataSourceEntity> findByTenantId(String tenantId);

    /** 按 id + 租户取单个，避免跨租户读取（D9）。 */
    Optional<DataSourceEntity> findByIdAndTenantId(UUID id, String tenantId);

    /** 租户内名称是否已存在（保存前去重）。 */
    boolean existsByTenantIdAndName(String tenantId, String name);
}
