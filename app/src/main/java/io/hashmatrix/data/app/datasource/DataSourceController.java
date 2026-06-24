package io.hashmatrix.data.app.datasource;

import io.hashmatrix.data.app.datasource.DataSourceCatalogService.PreviewResult;
import io.hashmatrix.data.app.datasource.DataSourceConnectionService.TestResult;
import io.hashmatrix.data.connector.spi.TableRef;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.ApiResponse;
import io.hashmatrix.starter.web.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源接入端点（M2 锚点链）。WP2 先落「测试连接」；保存/列表（WP3）、列库表/预览（WP4）后续接入。
 *
 * <p>返回走平台统一 {@link ApiResponse}。约定：<b>请求体非法</b>（缺字段/端口越界/类型不支持）→ 400；
 * <b>连接探测本身</b>无论连通与否都属一次成功的 API 调用 → 200，连通结果在 {@code data.ok}（便于前端
 * 「测试连接」按钮直接渲染成功/失败回显）。
 */
@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65535;

    private static final String TENANT_REQUIRED = "TENANT_REQUIRED";

    private final DataSourceConnectionService connectionService;
    private final DataSourceService dataSourceService;
    private final DataSourceCatalogService catalogService;

    public DataSourceController(
            DataSourceConnectionService connectionService,
            DataSourceService dataSourceService,
            DataSourceCatalogService catalogService) {
        this.connectionService = connectionService;
        this.dataSourceService = dataSourceService;
        this.catalogService = catalogService;
    }

    /**
     * 测试一条数据源连接（真连）。连不上返回 {@code ok=false} + 真实（脱敏）错误。
     *
     * @throws BusinessException 请求体非法（400）
     */
    @PostMapping("/test")
    public ApiResponse<TestConnectionResponse> test(@RequestBody TestConnectionRequest request) {
        validate(request);
        TestResult result =
                connectionService.test(
                        request.type().trim(),
                        request.host().trim(),
                        request.port(),
                        request.database().trim(),
                        request.username().trim(),
                        request.password());
        return ApiResponse.ok(new TestConnectionResponse(result.ok(), result.message()));
    }

    /**
     * 登记数据源（口令加密落库，D7）。强制本租户（D9）。同租户重名 → 409。
     *
     * @throws BusinessException 缺租户上下文（400）/ 请求体非法（400）/ 名称冲突（409）
     */
    @PostMapping
    public ApiResponse<DataSourceView> create(@RequestBody CreateDataSourceRequest request) {
        String tenantId = currentTenant();
        validate(request);
        DataSourceEntity saved =
                dataSourceService.create(
                        tenantId,
                        request.name().trim(),
                        request.type().trim(),
                        request.host().trim(),
                        request.port(),
                        request.database().trim(),
                        request.username().trim(),
                        request.password());
        return ApiResponse.ok(toView(saved));
    }

    /**
     * 列出<b>本租户</b>的数据源（D9）。响应<b>不含任何口令字段</b>。
     *
     * @throws BusinessException 缺租户上下文（400）
     */
    @GetMapping
    public ApiResponse<List<DataSourceView>> list() {
        String tenantId = currentTenant();
        List<DataSourceView> views =
                dataSourceService.list(tenantId).stream().map(DataSourceController::toView).toList();
        return ApiResponse.ok(views);
    }

    /**
     * 列出某数据源（本租户）下的库表（旁路·取结构）。
     *
     * @throws BusinessException 缺租户（400）/ 数据源不存在或非本租户（404）
     */
    @GetMapping("/{id}/tables")
    public ApiResponse<List<TableView>> tables(@PathVariable("id") String id) {
        String tenantId = currentTenant();
        List<TableView> tables =
                catalogService.listTables(tenantId, parseId(id)).stream()
                        .map(t -> new TableView(t.catalog(), t.schema(), t.name()))
                        .toList();
        return ApiResponse.ok(tables);
    }

    /**
     * 预览某表前 N 行（N 钳制并驱动侧封顶）。请求表名经元数据白名单校验（防注入）。
     *
     * @throws BusinessException 缺租户（400）/ 请求体非法（400）/ 数据源或表不存在（404）
     */
    @PostMapping("/{id}/preview")
    public ApiResponse<PreviewResponse> preview(
            @PathVariable("id") String id, @RequestBody PreviewRequest request) {
        String tenantId = currentTenant();
        if (request == null) {
            throw new BusinessException(VALIDATION_ERROR, "请求体不能为空");
        }
        requireText(request.table(), "table");
        PreviewResult result =
                catalogService.preview(
                        tenantId, parseId(id), request.table().trim(), request.schema(), request.limit());
        return ApiResponse.ok(
                new PreviewResponse(result.columns(), result.rows(), result.limit()));
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(VALIDATION_ERROR, "数据源 id 非法");
        }
    }

    /** 强制取当前租户（D9）：缺 {@code X-Tenant-Id} → 明确 400，而非静默放行或 500。 */
    private static String currentTenant() {
        return TenantContextHolder.getTenantId()
                .filter(t -> !t.isBlank())
                .orElseThrow(
                        () -> new BusinessException(TENANT_REQUIRED, "缺少租户上下文（X-Tenant-Id）"));
    }

    /** 实体 → 视图：刻意<b>不</b>暴露 {@code secret_cipher}/{@code secret_ref} 等任何凭据字段（D7）。 */
    private static DataSourceView toView(DataSourceEntity e) {
        return new DataSourceView(
                e.getId().toString(),
                e.getName(),
                e.getType(),
                e.getHost(),
                e.getPort(),
                e.getDatabaseName(),
                e.getUsername(),
                e.getCreatedAt());
    }

    private static void validate(CreateDataSourceRequest req) {
        if (req == null) {
            throw new BusinessException(VALIDATION_ERROR, "请求体不能为空");
        }
        requireText(req.name(), "name");
        requireText(req.type(), "type");
        requireText(req.host(), "host");
        requireText(req.database(), "database");
        requireText(req.username(), "username");
        // 保存的数据源须带口令（加密落库）；测试连接可无口令，但持久化登记要求显式提供。
        requireText(req.password(), "password");
        requirePort(req.port());
    }

    private static void validate(TestConnectionRequest req) {
        if (req == null) {
            throw new BusinessException(VALIDATION_ERROR, "请求体不能为空");
        }
        requireText(req.type(), "type");
        requireText(req.host(), "host");
        requireText(req.database(), "database");
        requireText(req.username(), "username");
        requirePort(req.port());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(VALIDATION_ERROR, field + " 不能为空");
        }
    }

    private static void requirePort(Integer port) {
        if (port == null || port < PORT_MIN || port > PORT_MAX) {
            throw new BusinessException(
                    VALIDATION_ERROR, "port 须在 " + PORT_MIN + "–" + PORT_MAX + " 之间");
        }
    }

    /**
     * 测试连接请求。
     *
     * @param type     数据源方言类型键（如 {@code mysql} / {@code postgresql}），驱动方言选择（D8）
     * @param host     主机
     * @param port     端口
     * @param database 库名
     * @param username 登录名
     * @param password 口令（临时、即用即弃；可空，用于无鉴权源）
     */
    public record TestConnectionRequest(
            String type, String host, Integer port, String database, String username, String password) {}

    /**
     * 测试连接结果。
     *
     * @param ok      是否连上且有效
     * @param message 成功为 {@code "connected"}；失败为脱敏后的真实错误
     */
    public record TestConnectionResponse(boolean ok, String message) {}

    /**
     * 登记数据源请求。{@code password} 为明文，仅用于服务端加密落库，<b>响应绝不回显</b>（D7）。
     *
     * @param name     数据源展示名（租户内唯一）
     * @param type     方言类型键（如 {@code mysql} / {@code postgresql}），D8
     * @param host     主机
     * @param port     端口
     * @param database 库名
     * @param username 登录名
     * @param password 口令明文（加密存 {@code secret_cipher}）
     */
    public record CreateDataSourceRequest(
            String name,
            String type,
            String host,
            Integer port,
            String database,
            String username,
            String password) {}

    /**
     * 数据源视图（对外）。<b>刻意不含任何口令字段</b>（明文/密文/引用一律不出，D7）。
     *
     * @param id        数据源 id
     * @param name      展示名
     * @param type      方言类型键
     * @param host      主机
     * @param port      端口
     * @param database  库名
     * @param username  登录名
     * @param createdAt 创建时间
     */
    public record DataSourceView(
            String id,
            String name,
            String type,
            String host,
            int port,
            String database,
            String username,
            Instant createdAt) {}

    /**
     * 库表视图。
     *
     * @param catalog 目录/库（可空）
     * @param schema  模式（可空，如 MySQL）
     * @param name    表名
     */
    public record TableView(String catalog, String schema, String name) {}

    /**
     * 预览请求。
     *
     * @param table  表名（必填，按真实元数据白名单校验）
     * @param schema 模式（可空，参与白名单匹配）
     * @param limit  期望行数（可空；钳制到 [1, {@value DataSourceCatalogService#MAX_PREVIEW_LIMIT}]）
     */
    public record PreviewRequest(String table, String schema, Integer limit) {}

    /**
     * 预览响应。
     *
     * @param columns 列名（保序）
     * @param rows    行（列名→值，保序）
     * @param limit   生效行上限
     */
    public record PreviewResponse(List<String> columns, List<Map<String, Object>> rows, int limit) {}
}
