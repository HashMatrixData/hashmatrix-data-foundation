package io.hashmatrix.data.app.datasource;

import io.hashmatrix.data.app.datasource.DataSourceConnectionService.TestResult;
import io.hashmatrix.starter.web.ApiResponse;
import io.hashmatrix.starter.web.BusinessException;
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

    private final DataSourceConnectionService connectionService;

    public DataSourceController(DataSourceConnectionService connectionService) {
        this.connectionService = connectionService;
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

    private static void validate(TestConnectionRequest req) {
        if (req == null) {
            throw new BusinessException(VALIDATION_ERROR, "请求体不能为空");
        }
        requireText(req.type(), "type");
        requireText(req.host(), "host");
        requireText(req.database(), "database");
        requireText(req.username(), "username");
        if (req.port() == null || req.port() < PORT_MIN || req.port() > PORT_MAX) {
            throw new BusinessException(VALIDATION_ERROR, "port 须在 " + PORT_MIN + "–" + PORT_MAX + " 之间");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(VALIDATION_ERROR, field + " 不能为空");
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
}
