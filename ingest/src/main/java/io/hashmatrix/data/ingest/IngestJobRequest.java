package io.hashmatrix.data.ingest;

import io.hashmatrix.data.connector.spi.TenantCatalog;

/**
 * 采集作业请求：哪个租户、采集哪张源表、受何配额约束。
 *
 * <p>{@code tenantId} 经 {@link TenantCatalog#catalogFor(String)} 解析为 per-tenant 隔离 catalog/database，
 * 作业的源/目标均落在该隔离域内，跨租户默认不可见。
 *
 * @param tenantId    租户标识（如 {@code acme} / {@code tenant-demo}）
 * @param sourceTable 源表名（脱敏占位）
 * @param quota       本作业适用的租户配额
 */
public record IngestJobRequest(String tenantId, String sourceTable, TenantQuota quota) {

    public IngestJobRequest {
        // 复用命名约定校验，顺带防注入；非法租户标识在此即失败
        TenantCatalog.catalogFor(tenantId);
        if (sourceTable == null || sourceTable.isBlank()) {
            throw new IllegalArgumentException("sourceTable must not be blank");
        }
        if (quota == null) {
            throw new IllegalArgumentException("quota must not be null");
        }
    }

    /** 本作业的 per-tenant 隔离 catalog/database（如 {@code df_acme}）。 */
    public String tenantCatalog() {
        return TenantCatalog.catalogFor(tenantId);
    }
}
