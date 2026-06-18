package io.hashmatrix.data.connector.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * 连接器注册表：经 {@link ServiceLoader} 发现 classpath 上所有 {@link Connector} 实现，按
 * {@link Connector#type()} 索引。这是「新增数据库 = 加一个插件 jar，不改主干」的发现入口。
 *
 * <p>实例不可变；按需 {@link #load()} 构建（如 app 启动时建一个单例 Bean）。
 */
public final class ConnectorRegistry {

    private final Map<String, Connector> byType;

    private ConnectorRegistry(Map<String, Connector> byType) {
        this.byType = byType;
    }

    /** 用当前线程上下文类加载器发现并构建注册表。 */
    public static ConnectorRegistry load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    /** 用指定类加载器发现并构建注册表。 */
    public static ConnectorRegistry load(ClassLoader classLoader) {
        Map<String, Connector> byType = new LinkedHashMap<>();
        for (Connector connector : ServiceLoader.load(Connector.class, classLoader)) {
            Connector previous = byType.putIfAbsent(connector.type(), connector);
            if (previous != null) {
                throw new ConnectorException(
                        "duplicate connector type '%s': %s vs %s"
                                .formatted(
                                        connector.type(),
                                        previous.getClass().getName(),
                                        connector.getClass().getName()));
            }
        }
        return new ConnectorRegistry(Map.copyOf(byType));
    }

    /** 已注册的全部类型键（稳定顺序）。 */
    public Set<String> types() {
        return byType.keySet();
    }

    /** 按类型键查找连接器。 */
    public Optional<Connector> find(String type) {
        return Optional.ofNullable(byType.get(type));
    }

    /**
     * 按类型键强制取用。
     *
     * @throws ConnectorException 无此类型的连接器（插件未上 classpath）
     */
    public Connector require(String type) {
        Connector connector = byType.get(type);
        if (connector == null) {
            throw new ConnectorException(
                    "no connector registered for type '%s' (registered: %s)".formatted(type, byType.keySet()));
        }
        return connector;
    }
}
