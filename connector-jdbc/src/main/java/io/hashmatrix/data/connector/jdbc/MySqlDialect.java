package io.hashmatrix.data.connector.jdbc;

/** 内置 MySQL 方言（标准库参考实现）。标识符用反引号。 */
public final class MySqlDialect implements Dialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public boolean matches(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:");
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return '`' + identifier.replace("`", "``") + '`';
    }
}
