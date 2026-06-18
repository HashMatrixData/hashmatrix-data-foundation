package io.hashmatrix.data.connector.spi;

import java.util.List;

/**
 * 表元数据：定位 + 列清单。{@link MetadataScanner#describeTable(TableRef)} 的产物，
 * 也是 {@link RecordReader#schema()} 的结构描述。
 *
 * @param ref     表定位
 * @param columns 列清单（稳定顺序，与读出 {@link Record} 字段顺序一致）
 */
public record TableMeta(TableRef ref, List<ColumnMeta> columns) {

    public TableMeta {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        columns = List.copyOf(columns);
    }

    /** 主键列名（按声明顺序）。 */
    public List<String> primaryKeyColumns() {
        return columns.stream().filter(ColumnMeta::primaryKey).map(ColumnMeta::name).toList();
    }
}
