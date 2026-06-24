package io.hashmatrix.data.app.datasource;

import io.hashmatrix.data.connector.jdbc.Dialect;
import io.hashmatrix.data.connector.jdbc.DialectRegistry;
import io.hashmatrix.data.connector.jdbc.JdbcConnector;
import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import io.hashmatrix.starter.web.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 数据源连接测试（WP2）：按 {@code type} 选方言拼 URL（<b>D8 连接器中立</b>）→ 复用
 * {@link JdbcConnector#connect} 真连 → 返回结构化结果。
 *
 * <p><b>凭据红线（D7）</b>：测试口令为<b>请求内联的临时口令</b>，经 {@link JdbcConnector} 的
 * 临时 {@code SecretResolver}「即用即弃」，<b>不</b>进 {@link DataSourceConfig}、不落库、不入日志；
 * 失败信息经 {@link #sanitize} 脱敏，绝不回显口令。
 */
@Service
public class DataSourceConnectionService {

    private static final String SECRET_REF_INLINE = "inline";

    private final DialectRegistry dialects;

    public DataSourceConnectionService(DialectRegistry dialects) {
        this.dialects = dialects;
    }

    /**
     * 测试一次连接。
     *
     * @return {@link TestResult#ok()} 连上且校验通过；否则 {@link TestResult#failed(String)} 带脱敏错误
     * @throws BusinessException 类型不受支持（无对应方言插件）—— 400
     */
    public TestResult test(
            String type, String host, int port, String database, String username, String password) {
        Dialect dialect =
                dialects.forType(type)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "UNSUPPORTED_DATASOURCE_TYPE",
                                                "不支持的数据源类型：" + type + "（缺对应方言插件）"));
        String url = dialect.jdbcUrl(host, port, database);

        // 临时口令经注入的 resolver 解析，明文不进 DataSourceConfig（D7）。无口令则不设 secretRef。
        boolean hasPassword = password != null && !password.isEmpty();
        JdbcConnector connector = new JdbcConnector(ref -> password);
        DataSourceConfig config =
                DataSourceConfig.builder(JdbcConnector.TYPE)
                        .url(url)
                        .username(username)
                        .secretRef(hasPassword ? SECRET_REF_INLINE : null)
                        .build();

        try (ConnectorConnection connection = connector.connect(config)) {
            return connection.isValid()
                    ? TestResult.success()
                    : TestResult.failed("连接已建立但有效性校验未通过");
        } catch (RuntimeException e) {
            return TestResult.failed(sanitize(rootMessage(e), password));
        }
    }

    /** 取异常链最深一层的信息（真实驱动报错更可读），无信息则用类名。 */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return (msg == null || msg.isBlank()) ? cur.getClass().getSimpleName() : msg;
    }

    /**
     * 错误信息脱敏（D7 防御性纵深）：纵然主流 JDBC 驱动从不回显口令，仍把口令子串抹成 {@code ***}，
     * 并限长，杜绝任何口令经错误信息外泄。
     */
    private static String sanitize(String message, String password) {
        String out = message;
        if (password != null && !password.isEmpty()) {
            out = out.replace(password, "***");
        }
        return out.length() > 500 ? out.substring(0, 500) + "…" : out;
    }

    /**
     * 连接测试结果。
     *
     * @param ok      是否连上且有效
     * @param message 失败原因（脱敏）；成功为 {@code "connected"}
     */
    public record TestResult(boolean ok, String message) {

        public static TestResult success() {
            return new TestResult(true, "connected");
        }

        public static TestResult failed(String message) {
            return new TestResult(false, message);
        }
    }
}
