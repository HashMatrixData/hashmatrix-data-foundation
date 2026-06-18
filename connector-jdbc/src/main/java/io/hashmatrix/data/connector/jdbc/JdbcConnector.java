package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.Connector;
import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 标准 JDBC 连接器（{@code type="jdbc"}）——参考实现。经 {@code META-INF/services} 注册，
 * 由 {@link io.hashmatrix.data.connector.spi.ConnectorRegistry} 发现。
 *
 * <p>方言差异下沉到 {@link Dialect} SPI；本连接器与采集主干不感知具体数据库。
 *
 * <p><b>凭据红线</b>：口令不来自配置明文，而是经 {@link DataSourceConfig#secretRef()} 指向的
 * 环境变量在运行期解析（{@link #resolvePassword}）；源码/配置/提交历史一律不含口令。
 */
public final class JdbcConnector implements Connector {

    /** 类型键。 */
    public static final String TYPE = "jdbc";

    private final DialectRegistry dialects = DialectRegistry.load();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean supports(DataSourceConfig config) {
        return TYPE.equals(config.type()) && config.url().startsWith("jdbc:");
    }

    @Override
    public ConnectorConnection connect(DataSourceConfig config) {
        Dialect dialect = dialects.forUrl(config.url());
        try {
            Connection connection = open(config);
            return new JdbcConnection(connection, dialect);
        } catch (SQLException e) {
            throw new ConnectorException("failed to open JDBC connection: " + config, e);
        }
    }

    private Connection open(DataSourceConfig config) throws SQLException {
        Properties props = new Properties();
        config.username().ifPresent(u -> props.setProperty("user", u));
        String password = resolvePassword(config);
        if (password != null) {
            props.setProperty("password", password);
        }
        // 透传方言/驱动级附加参数（如 sslmode）
        config.options().forEach(props::setProperty);
        return DriverManager.getConnection(config.url(), props);
    }

    /**
     * 从 {@code secretRef} 指向的环境变量解析口令（非明文落地）。无 {@code secretRef} 返回
     * {@code null}（如 H2 等无鉴权测试源）。生产可替换为 Vault/K8s Secret 解析器。
     */
    private String resolvePassword(DataSourceConfig config) {
        return config.secretRef().map(System::getenv).orElse(null);
    }
}
