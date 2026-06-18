package io.hashmatrix.data.connector.spi;

import java.util.List;

/**
 * 元数据扫描（旁路·取结构）。供 {@code governance} 元数据采集复用——只读结构、不搬数据。
 *
 * <p>所有方法以 per-tenant 隔离键（{@code catalog}）为入参，跨租户默认不可见。实现失败
 * 一律包装为 {@link ConnectorException}。
 */
public interface MetadataScanner {

    /** 列出某 catalog 下的 schema 名（按稳定顺序）。 */
    List<String> listSchemas(String catalog);

    /** 列出 {@code catalog.schema} 下的表。 */
    List<TableRef> listTables(String catalog, String schema);

    /** 取单表的结构（列、主键）。 */
    TableMeta describeTable(TableRef table);
}
