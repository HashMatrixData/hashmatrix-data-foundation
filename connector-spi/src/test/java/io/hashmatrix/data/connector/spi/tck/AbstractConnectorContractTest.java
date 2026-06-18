package io.hashmatrix.data.connector.spi.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.data.connector.spi.ColumnMeta;
import io.hashmatrix.data.connector.spi.Connector;
import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import io.hashmatrix.data.connector.spi.MetadataScanner;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.RecordReader;
import io.hashmatrix.data.connector.spi.RecordWriter;
import io.hashmatrix.data.connector.spi.TableMeta;
import io.hashmatrix.data.connector.spi.TableRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Connector SPI 契约测试套件（TCK）。任何 {@link Connector} 实现编写一个子类、提供下列钩子，
 * 即获得对「连接 / 元数据扫描 / 读写」三类能力的统一契约校验——参考实现 {@code connector-jdbc}
 * 与本模块自带的内存实现都跑同一套。
 *
 * <p>约定：被测实现须在 {@code @BeforeEach} 中把 {@link #table()} 重置为「恰好
 * {@link #seededRowCount()} 行」的已知脱敏数据（{@code acme} / {@code tenant-demo}），列含
 * {@link #expectedColumnNames()}，首列为主键。
 */
public abstract class AbstractConnectorContractTest {

    /** 被测连接器实现。 */
    protected abstract Connector connector();

    /** 指向被测数据源的配置（脱敏，不含明文口令）。 */
    protected abstract DataSourceConfig config();

    /** 已按约定 seed 的契约表（catalog/schema 为 per-tenant 隔离键）。 */
    protected abstract TableRef table();

    /** 契约表的期望列名（稳定顺序，首列为主键）。 */
    protected abstract List<String> expectedColumnNames();

    /** seed 行数。 */
    protected abstract int seededRowCount();

    /** 用于写入契约的一行新数据（主键不与已 seed 行冲突；脱敏）。 */
    protected abstract Record newRow();

    private ConnectorConnection open() {
        return connector().connect(config());
    }

    @Test
    void connects_and_reports_valid() {
        try (ConnectorConnection conn = open()) {
            assertThat(conn.isValid()).isTrue();
        }
    }

    @Test
    void supports_matches_config_type() {
        assertThat(connector().type()).isEqualTo(config().type());
        assertThat(connector().supports(config())).isTrue();
    }

    @Test
    void metadata_scan_lists_schema_and_table() {
        TableRef t = table();
        try (ConnectorConnection conn = open()) {
            MetadataScanner scanner = conn.metadataScanner();
            assertThat(scanner.listSchemas(t.catalog())).contains(t.schema());
            assertThat(scanner.listTables(t.catalog(), t.schema()))
                    .extracting(TableRef::name)
                    .contains(t.name());
        }
    }

    @Test
    void describe_table_exposes_columns_and_primary_key() {
        try (ConnectorConnection conn = open()) {
            TableMeta meta = conn.metadataScanner().describeTable(table());
            assertThat(meta.columns()).extracting(ColumnMeta::name).containsExactlyElementsOf(expectedColumnNames());
            assertThat(meta.primaryKeyColumns()).containsExactly(expectedColumnNames().get(0));
        }
    }

    @Test
    void reads_seeded_rows() {
        try (ConnectorConnection conn = open();
                RecordReader reader = conn.reader(table())) {
            assertThat(reader.schema().ref().name()).isEqualTo(table().name());
            List<Record> rows = drain(reader.read());
            assertThat(rows).hasSize(seededRowCount());
            assertThat(rows.get(0).asMap().keySet()).containsExactlyElementsOf(expectedColumnNames());
        }
    }

    @Test
    void writes_then_reads_back() {
        try (ConnectorConnection conn = open()) {
            try (RecordWriter writer = conn.writer(table())) {
                writer.write(newRow());
                writer.flush();
            }
            try (RecordReader reader = conn.reader(table())) {
                assertThat(drain(reader.read())).hasSize(seededRowCount() + 1);
            }
        }
    }

    @Test
    void require_unknown_table_fails_clearly() {
        try (ConnectorConnection conn = open()) {
            TableRef missing = new TableRef(table().catalog(), table().schema(), "does_not_exist_demo");
            assertThatThrownBy(() -> conn.metadataScanner().describeTable(missing))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private static List<Record> drain(Iterator<Record> it) {
        List<Record> out = new ArrayList<>();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }
}
