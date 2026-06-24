package io.hashmatrix.data.connector.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.RecordReader;
import io.hashmatrix.data.connector.spi.TableRef;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 有界读 {@link ConnectorConnection#reader(TableRef, int)}：行数经驱动侧 {@code setMaxRows} 封顶（预览/采样）。
 * 用 H2 验证（无需 docker），种 5 行、取 2 行。
 */
class JdbcBoundedReaderTest {

    private static final String URL = "jdbc:h2:mem:df_preview;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String USER = "sa";
    private static final TableRef TABLE = new TableRef(null, "df_acme", "orders");

    private final JdbcConnector connector = new JdbcConnector();

    @BeforeEach
    void seedFiveRows() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, "");
                Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS \"df_acme\" CASCADE");
            st.execute("CREATE SCHEMA \"df_acme\"");
            st.execute("CREATE TABLE \"df_acme\".\"orders\" (\"id\" BIGINT PRIMARY KEY, \"amount\" INT)");
            st.execute(
                    "INSERT INTO \"df_acme\".\"orders\" (\"id\",\"amount\") "
                            + "VALUES (1,10),(2,20),(3,30),(4,40),(5,50)");
        }
    }

    private DataSourceConfig config() {
        return DataSourceConfig.builder(JdbcConnector.TYPE).url(URL).username(USER).build();
    }

    @Test
    void bounded_reader_caps_rows_at_max() {
        try (ConnectorConnection conn = connector.connect(config());
                RecordReader reader = conn.reader(TABLE, 2)) {
            List<Record> rows = drain(reader.read());
            assertThat(rows).hasSize(2);
        }
    }

    @Test
    void zero_max_means_unbounded() {
        try (ConnectorConnection conn = connector.connect(config());
                RecordReader reader = conn.reader(TABLE, 0)) {
            assertThat(drain(reader.read())).hasSize(5);
        }
    }

    private static List<Record> drain(Iterator<Record> it) {
        List<Record> rows = new ArrayList<>();
        while (it.hasNext()) {
            rows.add(it.next());
        }
        return rows;
    }
}
