package io.hashmatrix.data.connector.jdbc;

/** 内置 PostgreSQL 方言（标准库参考实现）。ANSI 双引号标识符。 */
public final class PostgresDialect implements Dialect {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public boolean matches(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }
}
