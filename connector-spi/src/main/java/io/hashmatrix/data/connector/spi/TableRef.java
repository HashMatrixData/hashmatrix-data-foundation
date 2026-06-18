package io.hashmatrix.data.connector.spi;

/**
 * 表的三段式定位：{@code catalog.schema.name}。
 *
 * <p>多租户下 {@code catalog}（或 {@code schema}，依数据源而定）即 per-tenant 隔离键，
 * 通常由 {@link TenantCatalog#catalogFor(String)} 解析得到，跨租户默认不可见。
 *
 * @param catalog 目录/库（可空，部分数据源无 catalog 层级）
 * @param schema  模式（可空）
 * @param name    表名，非空白
 */
public record TableRef(String catalog, String schema, String name) {

    public TableRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("table name must not be blank");
        }
    }

    /** 仅有表名的引用。 */
    public static TableRef of(String name) {
        return new TableRef(null, null, name);
    }

    /** {@code schema.name} 引用。 */
    public static TableRef of(String schema, String name) {
        return new TableRef(null, schema, name);
    }

    /** 点分全限定名（省略空段），如 {@code df_acme.public.orders}。 */
    public String qualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (catalog != null && !catalog.isBlank()) {
            sb.append(catalog).append('.');
        }
        if (schema != null && !schema.isBlank()) {
            sb.append(schema).append('.');
        }
        return sb.append(name).toString();
    }
}
