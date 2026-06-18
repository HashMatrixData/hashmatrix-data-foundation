package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.ColumnMeta;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.RecordWriter;
import io.hashmatrix.data.connector.spi.TableMeta;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

/** 行写入（主链路·搬行）：按表结构生成 {@code INSERT}，批量缓冲、{@code flush} 落库。 */
final class JdbcRecordWriter implements RecordWriter {

    private final TableMeta schema;
    private final PreparedStatement statement;

    JdbcRecordWriter(Connection connection, Dialect dialect, TableMeta schema) {
        this.schema = schema;
        try {
            this.statement = connection.prepareStatement(insertSql(dialect, schema));
        } catch (SQLException e) {
            throw new ConnectorException("failed to open writer for " + schema.ref().qualifiedName(), e);
        }
    }

    private static String insertSql(Dialect dialect, TableMeta schema) {
        List<ColumnMeta> cols = schema.columns();
        StringJoiner names = new StringJoiner(", ", "(", ")");
        StringJoiner placeholders = new StringJoiner(", ", "(", ")");
        for (ColumnMeta col : cols) {
            names.add(dialect.quoteIdentifier(col.name()));
            placeholders.add("?");
        }
        return "INSERT INTO " + JdbcRecordReader.qualified(dialect, schema) + " " + names + " VALUES " + placeholders;
    }

    @Override
    public void write(Record record) {
        try {
            List<ColumnMeta> cols = schema.columns();
            for (int i = 0; i < cols.size(); i++) {
                statement.setObject(i + 1, record.get(cols.get(i).name()));
            }
            statement.addBatch();
        } catch (SQLException e) {
            throw new ConnectorException("failed to buffer row for " + schema.ref().qualifiedName(), e);
        }
    }

    @Override
    public void flush() {
        try {
            statement.executeBatch();
        } catch (SQLException e) {
            throw new ConnectorException("failed to flush writer for " + schema.ref().qualifiedName(), e);
        }
    }

    @Override
    public void close() {
        try {
            flush();
        } finally {
            try {
                statement.close();
            } catch (SQLException e) {
                throw new ConnectorException("failed to close writer", e);
            }
        }
    }
}
