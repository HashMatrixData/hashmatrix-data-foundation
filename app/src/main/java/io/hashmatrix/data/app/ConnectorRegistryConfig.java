package io.hashmatrix.data.app;

import io.hashmatrix.data.connector.jdbc.DialectRegistry;
import io.hashmatrix.data.connector.spi.ConnectorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动期经 {@link java.util.ServiceLoader} 装配连接器与方言注册表。
 *
 * <p>classpath 上每多一个 connector / dialect 插件 jar，注册表自动多发现一项——「加插件不动主干」
 * 在运行期的体现：本配置类无需为新库改动。
 */
@Configuration
public class ConnectorRegistryConfig {

    @Bean
    public ConnectorRegistry connectorRegistry() {
        return ConnectorRegistry.load();
    }

    @Bean
    public DialectRegistry dialectRegistry() {
        return DialectRegistry.load();
    }
}
