package io.hashmatrix.data.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** per-tenant catalog 命名与防注入校验。 */
class TenantCatalogTest {

    @Test
    void prefixes_tenant_id() {
        assertThat(TenantCatalog.catalogFor(MockTenants.ACME)).isEqualTo("df_acme");
        assertThat(TenantCatalog.catalogFor(MockTenants.TENANT_DEMO)).isEqualTo("df_tenant-demo");
    }

    @Test
    void distinct_tenants_get_distinct_catalogs() {
        assertThat(TenantCatalog.catalogFor(MockTenants.ACME))
                .isNotEqualTo(TenantCatalog.catalogFor(MockTenants.BETA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme", "a b", "drop;table", "../x", ""})
    void rejects_illegal_tenant_id(String bad) {
        assertThatThrownBy(() -> TenantCatalog.catalogFor(bad)).isInstanceOf(IllegalArgumentException.class);
    }
}
