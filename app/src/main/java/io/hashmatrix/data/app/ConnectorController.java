package io.hashmatrix.data.app;

import io.hashmatrix.data.connector.jdbc.DialectRegistry;
import io.hashmatrix.data.connector.spi.ConnectorRegistry;
import io.hashmatrix.data.connector.spi.TenantCatalog;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.ApiResponse;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 连接器/方言清单（租户感知）。返回结构走平台统一 {@link ApiResponse}。
 *
 * <p>租户上下文由网关注入的 {@code X-Tenant-*} 头经 starter-tenant 绑定（见
 * {@code icd/tenant-context-headers}）；此处展示当前租户的隔离 catalog（{@code df_<tenant>}）。
 */
@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorRegistry connectors;
    private final DialectRegistry dialects;

    public ConnectorController(ConnectorRegistry connectors, DialectRegistry dialects) {
        this.connectors = connectors;
        this.dialects = dialects;
    }

    /** 连接器/方言概览。租户缺省时 tenant/tenantCatalog 为 {@code null}（容忍直连/无上下文）。 */
    @GetMapping
    public ApiResponse<ConnectorsView> list() {
        String tenant = TenantContextHolder.getTenantId().orElse(null);
        String catalog = tenant == null ? null : TenantCatalog.catalogFor(tenant);
        return ApiResponse.ok(new ConnectorsView(tenant, catalog, connectors.types(), dialects.names()));
    }

    /**
     * 当前租户的隔离 catalog/database。<b>强制</b>需要租户上下文——无上下文即访问租户隔离资源属
     * 配置错误（{@link TenantContextHolder#requireTenantId()} 抛错），不静默放行（契约 §4）。
     */
    @GetMapping("/tenant-catalog")
    public ApiResponse<String> tenantCatalog() {
        return ApiResponse.ok(TenantCatalog.catalogFor(TenantContextHolder.requireTenantId()));
    }

    /**
     * 连接器/方言概览视图。
     *
     * @param tenant         当前租户标识（可空）
     * @param tenantCatalog  当前租户隔离 catalog（可空）
     * @param connectorTypes 已注册连接器类型
     * @param dialects       已发现 JDBC 方言
     */
    public record ConnectorsView(
            String tenant, String tenantCatalog, Set<String> connectorTypes, List<String> dialects) {}
}
