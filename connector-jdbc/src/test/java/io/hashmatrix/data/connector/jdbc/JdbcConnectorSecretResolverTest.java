package io.hashmatrix.data.connector.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link JdbcConnector} 的 {@link SecretResolver} 接缝：口令经注入的解析器解析，<b>不</b>取自
 * {@link DataSourceConfig} 明文（D7）。以带口令的 H2 内存库验证（无需 docker）。
 */
class JdbcConnectorSecretResolverTest {

    // 受口令保护的 H2 内存库；DB_CLOSE_DELAY=-1 让库存活至 JVM 退出。
    private static final String URL = "jdbc:h2:mem:df_secret;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "h2-demo-pwd"; // 脱敏占位

    @BeforeAll
    static void initPasswordProtectedDb() throws SQLException {
        // 首次连接即固化该库的 sa 口令。
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement st = c.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    @Test
    void resolves_password_via_injected_resolver_not_from_config() {
        // 注入解析器返回口令；DataSourceConfig 只带 secretRef（逻辑引用），不含明文。
        JdbcConnector connector = new JdbcConnector(ref -> "secret-ref".equals(ref) ? PASSWORD : null);
        DataSourceConfig config =
                DataSourceConfig.builder(JdbcConnector.TYPE)
                        .url(URL)
                        .username(USER)
                        .secretRef("secret-ref")
                        .build();

        try (ConnectorConnection conn = connector.connect(config)) {
            assertThat(conn.isValid()).isTrue();
        }
        // 明文不在配置的字符串表示里（红线）。
        assertThat(config.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void wrong_password_from_resolver_fails_to_connect() {
        JdbcConnector connector = new JdbcConnector(ref -> "wrong-pwd");
        DataSourceConfig config =
                DataSourceConfig.builder(JdbcConnector.TYPE)
                        .url(URL)
                        .username(USER)
                        .secretRef("secret-ref")
                        .build();

        assertThatThrownBy(() -> connector.connect(config)).isInstanceOf(ConnectorException.class);
    }

    @Test
    void default_constructor_resolves_from_env() {
        // 无参构造（ServiceLoader 用）默认走环境变量解析：取一个几乎必不存在的变量 → 无口令。
        JdbcConnector connector = new JdbcConnector();
        DataSourceConfig config =
                DataSourceConfig.builder(JdbcConnector.TYPE)
                        .url(URL)
                        .username(USER)
                        .secretRef("HASHMATRIX_NO_SUCH_ENV_VAR_FOR_TEST")
                        .build();

        // 解析为 null（无此 env）→ 不带口令 → 受保护库拒绝。
        assertThatThrownBy(() -> connector.connect(config)).isInstanceOf(ConnectorException.class);
    }
}
