package io.hashmatrix.data.connector.jdbc;

import io.hashmatrix.data.connector.spi.ColumnMeta;
import io.hashmatrix.data.connector.spi.ConnectorException;
import io.hashmatrix.data.connector.spi.MetadataScanner;
import io.hashmatrix.data.connector.spi.TableMeta;
import io.hashmatrix.data.connector.spi.TableRef;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 {@link DatabaseMetaData} 的元数据扫描（旁路·取结构）。{@code governance} 元数据采集可直接复用本实现。
 */
final class JdbcMetadataScanner implements MetadataScanner {

    private final Connection connection;

    JdbcMetadataScanner(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<String> listSchemas(String catalog) {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (catalog == null || catalog.equals(rs.getString("TABLE_CATALOG"))) {
                    schemas.add(rs.getString("TABLE_SCHEM"));
                }
            }
        } catch (SQLException e) {
            throw new ConnectorException("failed to list schemas (catalog=" + catalog + ")", e);
        }
        return schemas;
    }

    @Override
    public List<TableRef> listTables(String catalog, String schema) {
        List<TableRef> tables = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(catalog, schema, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                // 取结果集里的真实 catalog/schema（而非回显入参），保证元数据保真：
                // PG 返回真实 schema（如 public），MySQL 的 TABLE_SCHEM 本就为 null。
                tables.add(
                        new TableRef(
                                rs.getString("TABLE_CAT"),
                                rs.getString("TABLE_SCHEM"),
                                rs.getString("TABLE_NAME")));
            }
        } catch (SQLException e) {
            throw new ConnectorException("failed to list tables (%s.%s)".formatted(catalog, schema), e);
        }
        return tables;
    }

    @Override
    public TableMeta describeTable(TableRef table) {
        try {
            Set<String> primaryKeys = primaryKeys(table);
            List<ColumnMeta> columns = new ArrayList<>();
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(table.catalog(), table.schema(), table.name(), null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    columns.add(new ColumnMeta(
                            name,
                            rs.getString("TYPE_NAME"),
                            rs.getInt("DATA_TYPE"),
                            rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            primaryKeys.contains(name)));
                }
            }
            if (columns.isEmpty()) {
                throw new ConnectorException("table not found: " + table.qualifiedName());
            }
            return new TableMeta(table, columns);
        } catch (SQLException e) {
            throw new ConnectorException("failed to describe table: " + table.qualifiedName(), e);
        }
    }

    private Set<String> primaryKeys(TableRef table) throws SQLException {
        Set<String> keys = new LinkedHashSet<>();
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(table.catalog(), table.schema(), table.name())) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return keys;
    }
}
