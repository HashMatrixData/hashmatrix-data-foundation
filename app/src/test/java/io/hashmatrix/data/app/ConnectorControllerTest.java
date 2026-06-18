package io.hashmatrix.data.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.data.connector.jdbc.DialectRegistry;
import io.hashmatrix.data.connector.jdbc.JdbcConnector;
import io.hashmatrix.data.connector.spi.ConnectorRegistry;
import io.hashmatrix.starter.tenant.TenantContext;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.tenant.TenantContextMissingException;
import io.hashmatrix.starter.web.ApiResponse;
import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;

/**
 * 控制器单测（无需起容器）：验证 ServiceLoader 发现 JDBC 连接器 + 方言插件，且响应租户感知。
 * 方言插件以 runtime 作用域上 test classpath，故能发现 dm/kingbase/oceanbase。
 */
class ConnectorControllerTest {

    private final ConnectorController controller =
            new ConnectorController(ConnectorRegistry.load(), DialectRegistry.load());

    @Test
    void lists_jdbc_connector_and_all_dialects() {
        ConnectorController.ConnectorsView view = controller.list().data();

        assertThat(view.connectorTypes()).contains(JdbcConnector.TYPE);
        assertThat(view.dialects())
                .contains("postgresql", "mysql", "dm", "kingbase", "oceanbase", "ansi");
        assertThat(view.tenant()).isNull();
        assertThat(view.tenantCatalog()).isNull();
    }

    @Test
    void view_is_tenant_aware_when_context_bound() {
        ApiResponse<ConnectorController.ConnectorsView> resp =
                TenantContextHolder.callWith(TenantContext.of(MockTenants.ACME), controller::list);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.data().tenant()).isEqualTo("acme");
        assertThat(resp.data().tenantCatalog()).isEqualTo("df_acme");
    }

    @Test
    void tenant_catalog_requires_context() {
        assertThat(TenantContextHolder.callWith(TenantContext.of(MockTenants.ACME), controller::tenantCatalog).data())
                .isEqualTo("df_acme");

        assertThatThrownBy(controller::tenantCatalog).isInstanceOf(TenantContextMissingException.class);
    }
}
