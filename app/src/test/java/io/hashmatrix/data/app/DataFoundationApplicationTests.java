package io.hashmatrix.data.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.data.connector.spi.ConnectorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 上下文装配冒烟：starter-web / starter-tenant + JPA/Flyway/CryptoService 自动配置 + 注册表 Bean 正常加载。 */
@SpringBootTest
class DataFoundationApplicationTests extends AbstractPostgresTest {

    @Autowired
    private ConnectorRegistry connectorRegistry;

    @Test
    void context_loads_and_registry_is_wired() {
        assertThat(connectorRegistry).isNotNull();
        assertThat(connectorRegistry.types()).contains("jdbc");
    }
}
