package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.ColumnMeta;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.RecordReader;
import io.hashmatrix.data.connector.spi.TableMeta;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** 行读取（主链路·搬行）：按表结构 {@code SELECT}，惰性迭代结果集。 */
final class JdbcRecordReader implements RecordReader {

    private final TableMeta schema;
    private final PreparedStatement statement;
    private final ResultSet resultSet;

    JdbcRecordReader(Connection connection, Dialect dialect, TableMeta schema) {
        this(connection, dialect, schema, 0);
    }

    /**
     * @param maxRows 结果集行上限（{@code > 0} 时经 {@link PreparedStatement#setMaxRows} 在驱动侧封顶，
     *     用于预览/采样）；{@code <= 0} 表示不限。
     */
    JdbcRecordReader(Connection connection, Dialect dialect, TableMeta schema, int maxRows) {
        this.schema = schema;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement(selectSql(dialect, schema));
            if (maxRows > 0) {
                stmt.setMaxRows(maxRows);
            }
            rs = stmt.executeQuery();
        } catch (SQLException e) {
            // executeQuery 失败时 prepareStatement 已打开，须 best-effort 关闭，避免游标/语句句柄泄漏
            closeQuietly(rs);
            closeQuietly(stmt);
            throw new ConnectorException("failed to open reader for " + schema.ref().qualifiedName(), e);
        }
        this.statement = stmt;
        this.resultSet = rs;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private static String selectSql(Dialect dialect, TableMeta schema) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<ColumnMeta> cols = schema.columns();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(dialect.quoteIdentifier(cols.get(i).name()));
        }
        return sql.append(" FROM ").append(qualified(dialect, schema)).toString();
    }

    static String qualified(Dialect dialect, TableMeta schema) {
        String schemaName = schema.ref().schema();
        String prefix = (schemaName == null || schemaName.isBlank()) ? "" : dialect.quoteIdentifier(schemaName) + ".";
        return prefix + dialect.quoteIdentifier(schema.ref().name());
    }

    @Override
    public TableMeta schema() {
        return schema;
    }

    @Override
    public Iterator<Record> read() {
        return new Iterator<>() {
            private Boolean hasNext;

            @Override
            public boolean hasNext() {
                if (hasNext == null) {
                    try {
                        hasNext = resultSet.next();
                    } catch (SQLException e) {
                        throw new ConnectorException("failed to advance reader", e);
                    }
                }
                return hasNext;
            }

            @Override
            public Record next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasNext = null;
                return readRow();
            }
        };
    }

    private Record readRow() {
        Record.Builder row = Record.builder();
        try {
            for (ColumnMeta col : schema.columns()) {
                row.set(col.name(), resultSet.getObject(col.name()));
            }
        } catch (SQLException e) {
            throw new ConnectorException("failed to read row from " + schema.ref().qualifiedName(), e);
        }
        return row.build();
    }

    @Override
    public void close() {
        try {
            resultSet.close();
        } catch (SQLException ignored) {
            // best-effort close of result set
        }
        try {
            statement.close();
        } catch (SQLException e) {
            throw new ConnectorException("failed to close reader", e);
        }
    }
}
