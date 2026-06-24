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
     * JDBC URL 子协议（{@code jdbc:} 之后、{@code ://} 之前那段，如 {@code "mysql"}、{@code "postgresql"}）。
     * 默认等于 {@link #name()}；当方言名与子协议不一致时（如人大金仓 name=kingbase 而子协议=kingbase8）由插件覆盖。
     */
    default String jdbcSubprotocol() {
        return name();
    }

    /**
     * 由「主机/端口/库名」构建标准 JDBC URL —— <b>URL 形状是方言的职责</b>（D8 连接器中立）：
     * 端点/服务一律调本方法，绝不在上层写死某库的 URL 拼法；新增方言只改插件、不动主干。
     *
     * <p>默认形状 {@code jdbc:<subprotocol>://<host>:<port>/<database>} 覆盖 MySQL/PostgreSQL 等主流库；
     * URL 形状特殊的方言覆盖本方法即可。
     */
    default String jdbcUrl(String host, int port, String database) {
        return "jdbc:%s://%s:%d/%s".formatted(jdbcSubprotocol(), host, port, database);
    }

    /**
     * 标识符引用（防关键字/大小写歧义）。默认 ANSI 双引号，内部双引号转义为两个。
     * 用反引号或其它规则的方言覆盖本方法即可。
     */
    default String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
