package io.hashmatrix.data.connector.spi;

import java.util.regex.Pattern;

/**
 * 租户隔离键解析：per-tenant catalog/database 命名约定。
 *
 * <p>采集 / 计算 / 湖仓统一按租户隔离到独立 catalog/database（Doris/Paimon per-tenant 亦遵此约定）：
 * {@code catalogFor("acme") -> "df_acme"}。隔离键由调用方在跨租户操作前解析后下传 SPI，
 * SPI 自身不读线程级租户上下文（保持纯净、可被旁路链路复用）。
 *
 * <p>纯函数、零依赖；当前租户标识由 app 层经 {@code starter-tenant} 的
 * {@code TenantContextHolder.requireTenantId()} 取得后传入。
 */
public final class TenantCatalog {

    /** 数据底座 per-tenant catalog/database 前缀。 */
    public static final String PREFIX = "df_";

    /** 合法租户标识：小写字母/数字/连字符，避免 SQL 标识符注入与跨源命名歧义。 */
    private static final Pattern TENANT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");

    private TenantCatalog() {
        throw new AssertionError("no instances");
    }

    /**
     * 解析租户的隔离 catalog/database 名。
     *
     * @param tenantId 租户标识（如 {@code acme} / {@code tenant-demo}）
     * @return per-tenant 隔离键（如 {@code df_acme}）
     * @throws IllegalArgumentException 标识非法（防注入）
     */
    public static String catalogFor(String tenantId) {
        if (tenantId == null || !TENANT_ID.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("illegal tenantId: " + tenantId);
        }
        return PREFIX + tenantId;
    }
}
