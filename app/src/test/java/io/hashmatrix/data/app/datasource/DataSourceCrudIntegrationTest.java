package io.hashmatrix.data.app.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hashmatrix.data.app.AbstractPostgresTest;
import io.hashmatrix.data.app.crypto.CryptoService;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code POST/GET /api/datasources} 端到端（Testcontainers PG）：保存（口令加密落库）、按租户列表、
 * <b>两租户隔离</b>、响应不含口令、重名 409、缺租户 400。守 <b>D7</b>（密文落库）+ <b>D9</b>（租户隔离）。
 *
 * <p>容器在本类内跨方法共享，故各用例用<b>独立数据源名</b>、断言用 contains（不依赖全局计数）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataSourceCrudIntegrationTest extends AbstractPostgresTest {

    private static final String HEADER_TENANT = "X-Tenant-Id";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private DataSourceRepository repository;
    @Autowired private CryptoService crypto;

    private Map<String, Object> request(String name, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", "mysql");
        m.put("host", "mysql.example.com");
        m.put("port", 3306);
        m.put("database", "acme");
        m.put("username", "demo");
        m.put("password", password);
        return m;
    }

    private MockHttpServletRequestBuilder createAs(String tenant, Map<String, Object> body) throws Exception {
        return post("/api/datasources")
                .header(HEADER_TENANT, tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    @Test
    void create_then_list_round_trip_within_tenant() throws Exception {
        String name = "ds-roundtrip";
        mvc.perform(createAs(MockTenants.ACME, request(name, "p@ss-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.username").value("demo"));

        mvc.perform(get("/api/datasources").header(HEADER_TENANT, MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").exists());
    }

    @Test
    void response_never_contains_password_or_cipher() throws Exception {
        String secret = "LEAKCHECK-PWD-do-not-show";
        String createBody =
                mvc.perform(createAs(MockTenants.ACME, request("ds-noleak", secret)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(createBody).doesNotContain(secret);
        assertThat(createBody).doesNotContain("password").doesNotContain("secret");

        mvc.perform(get("/api/datasources").header(HEADER_TENANT, MockTenants.ACME))
                .andExpect(content().string(Matchers.not(Matchers.containsString(secret))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("password"))));
    }

    @Test
    void password_is_stored_encrypted_and_decryptable() throws Exception {
        String name = "ds-cipher";
        String plaintext = "cipher-check-demo";
        mvc.perform(createAs(MockTenants.ACME, request(name, plaintext))).andExpect(status().isOk());

        DataSourceEntity stored =
                repository.findByTenantId(MockTenants.ACME).stream()
                        .filter(e -> name.equals(e.getName()))
                        .findFirst()
                        .orElseThrow();

        // 密文 != 明文（确实加密落库），但能解回原文（往返正确）。
        assertThat(stored.getSecretCipher()).isNotEqualTo(plaintext);
        assertThat(stored.getSecretCipher()).doesNotContain(plaintext);
        assertThat(crypto.decrypt(stored.getSecretCipher())).isEqualTo(plaintext);
        assertThat(stored.getTenantId()).isEqualTo(MockTenants.ACME);
    }

    @Test
    void two_tenants_are_isolated() throws Exception {
        String acmeOnly = "ds-acme-private";
        mvc.perform(createAs(MockTenants.ACME, request(acmeOnly, "x-demo"))).andExpect(status().isOk());

        // 另一租户列表里看不到 acme 的数据源（D9）。
        mvc.perform(get("/api/datasources").header(HEADER_TENANT, MockTenants.BETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + acmeOnly + "')]").doesNotExist());

        // 同名在另一租户可独立存在（跨租户不冲突）。
        mvc.perform(createAs(MockTenants.BETA, request(acmeOnly, "y-demo"))).andExpect(status().isOk());
    }

    @Test
    void duplicate_name_within_tenant_conflicts() throws Exception {
        String name = "ds-dup";
        mvc.perform(createAs(MockTenants.ACME, request(name, "a-demo"))).andExpect(status().isOk());
        mvc.perform(createAs(MockTenants.ACME, request(name, "b-demo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NAME_CONFLICT"));
    }

    @Test
    void missing_tenant_header_is_bad_request() throws Exception {
        // 无 X-Tenant-Id：写与读都明确 400（D9 强制租户）。
        mvc.perform(
                        post("/api/datasources")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(request("ds-notenant", "p"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));

        mvc.perform(get("/api/datasources"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    void missing_password_is_validation_error() throws Exception {
        Map<String, Object> body = request("ds-nopwd", "ignored");
        body.remove("password");
        mvc.perform(createAs(MockTenants.ACME, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
