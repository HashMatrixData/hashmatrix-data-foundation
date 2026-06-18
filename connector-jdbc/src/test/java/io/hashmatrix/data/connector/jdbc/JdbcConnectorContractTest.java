package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.Connector;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.TableRef;
import io.hashmatrix.data.connector.spi.TenantCatalog;
import io.hashmatrix.data.connector.spi.tck.AbstractConnectorContractTest;
import io.hashmatrix.test.fixtures.MockTenants;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/**
 * JDBC 参考实现的契约测试：以 H2 充当轻量参考数据源（无需 docker），跑 connector-spi 的同一套 TCK。
 * 隔离模型 = <b>schema-per-tenant</b>：schema 名 = {@code df_acme}（{@code acme} 租户，全脱敏）。
 */
class JdbcConnectorContractTest extends AbstractConnectorContractTest {

    // DATABASE_TO_UPPER=FALSE 保持标识符原样小写；DB_CLOSE_DELAY=-1 让内存库存活至 JVM 退出
    private static final String URL = "jdbc:h2:mem:df_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String USER = "sa";
    private static final String SCHEMA = TenantCatalog.catalogFor(MockTenants.ACME); // df_acme
    private static final TableRef TABLE = new TableRef(null, SCHEMA, "orders");

    private final Connector connector = new JdbcConnector();

    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, "");
                Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
            st.execute("CREATE SCHEMA \"" + SCHEMA + "\"");
            st.execute("CREATE TABLE \"" + SCHEMA + "\".\"orders\" ("
                    + "\"id\" BIGINT PRIMARY KEY, "
                    + "\"tenant\" VARCHAR(64) NOT NULL, "
                    + "\"amount\" NUMERIC(12,2))");
            st.execute("INSERT INTO \"" + SCHEMA + "\".\"orders\" (\"id\",\"tenant\",\"amount\") "
                    + "VALUES (1,'acme',100.00),(2,'acme',250.00)");
        }
    }

    @Override
    protected Connector connector() {
        return connector;
    }

    @Override
    protected DataSourceConfig config() {
        return DataSourceConfig.builder(JdbcConnector.TYPE).url(URL).username(USER).build();
    }

    @Override
    protected TableRef table() {
        return TABLE;
    }

    @Override
    protected List<String> expectedColumnNames() {
        return List.of("id", "tenant", "amount");
    }

    @Override
    protected int seededRowCount() {
        return 2;
    }

    @Override
    protected Record newRow() {
        return Record.builder().set("id", 3L).set("tenant", MockTenants.ACME).set("amount", 375.00).build();
    }
}
