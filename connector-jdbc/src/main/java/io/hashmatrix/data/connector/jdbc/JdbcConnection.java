package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.MetadataScanner;
import io.hashmatrix.data.connector.spi.RecordReader;
import io.hashmatrix.data.connector.spi.RecordWriter;
import io.hashmatrix.data.connector.spi.TableRef;
import java.sql.Connection;
import java.sql.SQLException;

/** 一条 JDBC 连接的 SPI 适配：旁路扫描 + 主链路读写共用同一 {@link Connection}。 */
final class JdbcConnection implements ConnectorConnection {

    private final Connection connection;
    private final Dialect dialect;

    JdbcConnection(Connection connection, Dialect dialect) {
        this.connection = connection;
        this.dialect = dialect;
    }

    @Override
    public MetadataScanner metadataScanner() {
        return new JdbcMetadataScanner(connection);
    }

    @Override
    public RecordReader reader(TableRef table) {
        return new JdbcRecordReader(connection, dialect, metadataScanner().describeTable(table));
    }

    @Override
    public RecordReader reader(TableRef table, int maxRows) {
        // 行上限经 Statement.setMaxRows 在驱动侧下推——方言中立，无需写 LIMIT/TOP/ROWNUM。
        return new JdbcRecordReader(connection, dialect, metadataScanner().describeTable(table), maxRows);
    }

    @Override
    public RecordWriter writer(TableRef table) {
        return new JdbcRecordWriter(connection, dialect, metadataScanner().describeTable(table));
    }

    @Override
    public boolean isValid() {
        try {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new ConnectorException("failed to close JDBC connection", e);
        }
    }
}
