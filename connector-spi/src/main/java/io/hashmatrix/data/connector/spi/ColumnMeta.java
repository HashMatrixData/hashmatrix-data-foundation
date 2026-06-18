package io.hashmatrix.data.connector.spi;

/**
 * 列元数据（结构信息，供元数据采集旁路与读写主链路共用）。
 *
 * @param name       列名，非空白
 * @param typeName   数据源原生类型名（如 {@code varchar}、{@code int8}），保留原貌不强转
 * @param jdbcType   {@link java.sql.Types} 常量值；非 JDBC 源可填 {@link java.sql.Types#OTHER}
 * @param nullable   是否可空
 * @param primaryKey 是否主键成员
 */
public record ColumnMeta(String name, String typeName, int jdbcType, boolean nullable, boolean primaryKey) {

    public ColumnMeta {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("column name must not be blank");
        }
    }
}
