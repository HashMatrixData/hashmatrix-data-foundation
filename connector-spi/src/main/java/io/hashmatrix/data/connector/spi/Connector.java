package io.hashmatrix.data.connector.spi;

/**
 * 连接器 SPI 主干契约——数据源接入的唯一扩展点。
 *
 * <p><b>加插件不动主干</b>：每种数据源实现本接口，并在实现 jar 的
 * {@code META-INF/services/io.hashmatrix.data.connector.spi.Connector} 中注册一行实现类名，
 * 即被 {@link java.util.ServiceLoader} 发现、由 {@link ConnectorRegistry} 按 {@link #type()} 选取。
 * 主干（本模块及 app）零改动。
 *
 * <p>实现须无参可构造（{@code ServiceLoader} 约束）。
 */
public interface Connector {

    /** 类型键（全局唯一，如 {@code "jdbc"}）；{@link ConnectorRegistry} 据此选取。 */
    String type();

    /** 是否能处理该配置（默认按 {@code type} 匹配；实现可细化，如按 URL 前缀）。 */
    default boolean supports(DataSourceConfig config) {
        return type().equals(config.type());
    }

    /**
     * 按配置打开一条连接。
     *
     * @throws ConnectorException 连接失败（底层异常已包装）
     */
    ConnectorConnection connect(DataSourceConfig config);
}
