package io.hashmatrix.data.connector.spi;

import io.hashmatrix.data.connector.spi.tck.AbstractConnectorContractTest;
import io.hashmatrix.data.connector.spi.testfixture.InMemoryConnector;
import io.hashmatrix.data.connector.spi.testfixture.InMemoryStore;
import io.hashmatrix.test.fixtures.MockTenants;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/**
 * 用进程内实现自验证 TCK：证明契约套件本身可用，并示范第二种 SPI 实现跑同一契约。
 * 数据全部脱敏（{@code acme} 租户 catalog = {@code df_acme}）。
 */
class InMemoryConnectorContractTest extends AbstractConnectorContractTest {

    private static final String URL = "inmem://contract-demo";
    private static final String CATALOG = TenantCatalog.catalogFor(MockTenants.ACME); // df_acme
    private static final TableRef TABLE = new TableRef(CATALOG, "public", "orders");

    private final InMemoryConnector connector = new InMemoryConnector();

    @BeforeEach
    void seed() {
        InMemoryStore.reset(URL);
        InMemoryStore.defineTable(
                URL,
                new TableMeta(
                        TABLE,
                        List.of(
                                new ColumnMeta("id", "bigint", Types.BIGINT, false, true),
                                new ColumnMeta("tenant", "varchar", Types.VARCHAR, false, false),
                                new ColumnMeta("amount", "numeric", Types.NUMERIC, true, false))));
        InMemoryStore.seed(
                URL,
                TABLE,
                List.of(
                        row(1L, MockTenants.ACME, 100),
                        row(2L, MockTenants.ACME, 250)));
    }

    private static Record row(long id, String tenant, int amount) {
        return Record.builder().set("id", id).set("tenant", tenant).set("amount", amount).build();
    }

    @Override
    protected Connector connector() {
        return connector;
    }

    @Override
    protected DataSourceConfig config() {
        return DataSourceConfig.builder(InMemoryConnector.TYPE).url(URL).build();
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
        return row(3L, MockTenants.ACME, 375);
    }
}
