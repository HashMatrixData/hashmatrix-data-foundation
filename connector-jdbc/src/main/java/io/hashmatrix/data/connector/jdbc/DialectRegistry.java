package io.hashmatrix.data.connector.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 方言注册表：经 {@link ServiceLoader} 发现全部 {@link Dialect}，按 JDBC URL 选取最匹配者，
 * 无专用方言命中时回退 {@link DefaultDialect}。这是「新增数据库 = 加一个方言插件 jar」的选取入口。
 */
public final class DialectRegistry {

    private final List<Dialect> dialects;
    private final Dialect fallback = new DefaultDialect();

    private DialectRegistry(List<Dialect> dialects) {
        this.dialects = dialects;
    }

    public static DialectRegistry load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public static DialectRegistry load(ClassLoader classLoader) {
        List<Dialect> found = new ArrayList<>();
        for (Dialect dialect : ServiceLoader.load(Dialect.class, classLoader)) {
            found.add(dialect);
        }
        return new DialectRegistry(List.copyOf(found));
    }

    /** 按 URL 选取方言；无命中回退 ANSI 兜底方言。 */
    public Dialect forUrl(String jdbcUrl) {
        return dialects.stream().filter(d -> d.matches(jdbcUrl)).findFirst().orElse(fallback);
    }

    /** 已发现的方言名（含兜底）。 */
    public List<String> names() {
        List<String> names = new ArrayList<>(dialects.stream().map(Dialect::name).toList());
        names.add(fallback.name());
        return names;
    }
}
