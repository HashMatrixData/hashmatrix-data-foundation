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
 * <p><b>凭据红线</b>：口令不来自配置明文，而是经 {@link DataSourceConfig#secretRef()} 指向的逻辑引用，
 * 由 {@link SecretResolver} 在运行期解析（默认指向环境变量）；源码/配置/提交历史一律不含口令。
 * 应用层可注入自定义解析器（如解密 PG 密文、测试连接的临时口令），明文不入配置/库/日志（D7）。
 */
public final class JdbcConnector implements Connector {

    /** 类型键。 */
    public static final String TYPE = "jdbc";

    private final DialectRegistry dialects = DialectRegistry.load();
    private final SecretResolver secretResolver;

    /** 默认构造（{@code ServiceLoader} 约束的无参构造）：口令从环境变量解析。 */
    public JdbcConnector() {
        this(SecretResolver.FROM_ENV);
    }

    /**
     * 注入自定义口令解析器。用于应用层持有运行期口令（解密密文 / 测试连接的临时口令）的场景——
     * 明文经解析器即用即弃，不写入 {@link DataSourceConfig}（D7）。
     */
    public JdbcConnector(SecretResolver secretResolver) {
        this.secretResolver = secretResolver == null ? SecretResolver.FROM_ENV : secretResolver;
    }

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
     * 经注入的 {@link SecretResolver} 把 {@code secretRef} 解析为明文口令（非明文落地）。
     * 无 {@code secretRef} 返回 {@code null}（如 H2 等无鉴权测试源）。
     */
    private String resolvePassword(DataSourceConfig config) {
        return config.secretRef().map(secretResolver::resolve).orElse(null);
    }
}
