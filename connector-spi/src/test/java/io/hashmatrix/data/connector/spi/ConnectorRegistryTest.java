package io.hashmatrix.data.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.data.connector.spi.testfixture.InMemoryConnector;
import org.junit.jupiter.api.Test;

/** 验证「加插件不动主干」：ServiceLoader 发现 + 按类型取用。 */
class ConnectorRegistryTest {

    @Test
    void discovers_connector_via_service_loader() {
        ConnectorRegistry registry = ConnectorRegistry.load();
        assertThat(registry.types()).contains(InMemoryConnector.TYPE);
        assertThat(registry.require(InMemoryConnector.TYPE)).isInstanceOf(InMemoryConnector.class);
    }

    @Test
    void unknown_type_fails_with_clear_message() {
        ConnectorRegistry registry = ConnectorRegistry.load();
        assertThatThrownBy(() -> registry.require("no-such-connector"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("no-such-connector");
    }
}
