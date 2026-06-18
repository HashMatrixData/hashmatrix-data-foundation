package io.hashmatrix.data.connector.jdbc;

/**
 * ANSI 兜底方言：任何未被专用方言匹配的 JDBC URL 落到此处（如 H2 等标准 SQL 数据源）。
 * 仅用 ANSI 双引号标识符，不做任何数据库特异处理。
 */
public final class DefaultDialect implements Dialect {

    @Override
    public String name() {
        return "ansi";
    }

    /** 兜底方言不主动声明匹配；由 {@link DialectRegistry} 在无专用方言命中时回退选用。 */
    @Override
    public boolean matches(String jdbcUrl) {
        return false;
    }
}
