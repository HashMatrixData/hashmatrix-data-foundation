package io.hashmatrix.data.connector.jdbc;

/**
 * JDBC 方言 SPI——数据库差异的唯一收敛点（信创刚需）。
 *
 * <p><b>加插件不动主干</b>：每种数据库（达梦 / 人大金仓 / OceanBase …）实现本接口，并在方言插件 jar 的
 * {@code META-INF/services/io.hashmatrix.data.connector.jdbc.Dialect} 注册，即被
 * {@link java.util.ServiceLoader} 发现、由 {@link DialectRegistry} 按 JDBC URL 选取。
 * connector-jdbc 与其上的采集主干零改动。
 *
 * <p>实现须无参可构造。
 */
public interface Dialect {

    /** 方言名（如 {@code "postgresql"}、{@code "dm"}），用于日志与显式选取。 */
    String name();

    /** 是否匹配该 JDBC URL（通常按 {@code jdbc:<subprotocol>:} 前缀判定）。 */
    boolean matches(String jdbcUrl);

    /**
     * 标识符引用（防关键字/大小写歧义）。默认 ANSI 双引号，内部双引号转义为两个。
     * 用反引号或其它规则的方言覆盖本方法即可。
     */
    default String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
