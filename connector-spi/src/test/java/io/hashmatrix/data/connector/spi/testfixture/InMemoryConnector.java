package io.hashmatrix.data.connector.spi.testfixture;

import io.hashmatrix.data.connector.spi.Connector;
import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.ConnectorException;
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

/**
 * 进程内 {@link Connector} 实现（仅测试用）：用最小代价自验证 TCK 与 {@code ServiceLoader} 发现，
 * 同时示范「实现 SPI + {@code META-INF/services} 注册 = 接入一种数据源」的姿势。
 *
 * <p>经 {@code src/test/resources/META-INF/services/io.hashmatrix.data.connector.spi.Connector} 注册。
 */
public final class InMemoryConnector implements Connector {

    /** 类型键。 */
    public static final String TYPE = "inmem";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ConnectorConnection connect(DataSourceConfig config) {
        return new InMemoryConnection(config.url());
    }

    private static final class InMemoryConnection implements ConnectorConnection {
        private final String url;
        private boolean open = true;

        InMemoryConnection(String url) {
            this.url = url;
        }

        @Override
        public MetadataScanner metadataScanner() {
            return new InMemoryScanner(url);
        }

        @Override
        public RecordReader reader(TableRef table) {
            InMemoryStore.Table t = InMemoryStore.table(url, table);
            return new InMemoryReader(t.meta, new ArrayList<>(t.rows).iterator());
        }

        @Override
        public RecordWriter writer(TableRef table) {
            return new InMemoryWriter(InMemoryStore.table(url, table));
        }

        @Override
        public boolean isValid() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private record InMemoryScanner(String url) implements MetadataScanner {
        @Override
        public List<String> listSchemas(String catalog) {
            return InMemoryStore.db(url).values().stream()
                    .map(t -> t.meta.ref())
                    .filter(r -> java.util.Objects.equals(r.catalog(), catalog))
                    .map(TableRef::schema)
                    .distinct()
                    .toList();
        }

        @Override
        public List<TableRef> listTables(String catalog, String schema) {
            return InMemoryStore.db(url).values().stream()
                    .map(t -> t.meta.ref())
                    .filter(r -> java.util.Objects.equals(r.catalog(), catalog)
                            && java.util.Objects.equals(r.schema(), schema))
                    .toList();
        }

        @Override
        public TableMeta describeTable(TableRef table) {
            InMemoryStore.Table t = InMemoryStore.db(url).get(table.qualifiedName());
            if (t == null) {
                throw new ConnectorException("no such table: " + table.qualifiedName());
            }
            return t.meta;
        }
    }

    private record InMemoryReader(TableMeta schema, Iterator<Record> it) implements RecordReader {
        @Override
        public Iterator<Record> read() {
            return it;
        }

        @Override
        public void close() {
            // no-op: in-memory iterator holds no resources
        }
    }

    private record InMemoryWriter(InMemoryStore.Table table) implements RecordWriter {
        @Override
        public void write(Record record) {
            table.rows.add(record);
        }

        @Override
        public void flush() {
            // no-op: writes land immediately
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
