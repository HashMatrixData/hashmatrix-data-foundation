package io.hashmatrix.data.app.datasource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hashmatrix.data.app.AbstractPostgresTest;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /{id}/tables} + {@code POST /{id}/preview} 端到端（Testcontainers PG）：把本 PG 容器既当
 * 平台自身存储、又登记为一个 {@code postgresql} 型被连接源（其 {@code data_source} 表即现成可列/预览目标）。
 *
 * <p>覆盖：列出 {@code data_source}、预览有列有行、跨租户 404（D9）、未知表 404（防注入白名单）、行上限钳制。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataSourceCatalogIntegrationTest extends AbstractPostgresTest {

    private static final String HEADER_TENANT = "X-Tenant-Id";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    /** 登记一个指向本 PG 容器的数据源（postgresql 型），返回其 id。 */
    private String registerSelfPointingDataSource(String tenant, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "postgresql");
        body.put("host", pgHost());
        body.put("port", pgPort());
        body.put("database", pgDatabase());
        body.put("username", pgUsername());
        body.put("password", pgPassword());
        String resp =
                mvc.perform(
                                post("/api/datasources")
                                        .header(HEADER_TENANT, tenant)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(resp).at("/data/id").asText();
    }

    @Test
    void lists_tables_including_data_source() throws Exception {
        String id = registerSelfPointingDataSource(MockTenants.ACME, "pg-self-tables");
        mvc.perform(get("/api/datasources/" + id + "/tables").header(HEADER_TENANT, MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                // 现成的 data_source 表应出现在列表里，且带真实 schema（public，非回显 null）。
                .andExpect(jsonPath("$.data[?(@.name == 'data_source' && @.schema == 'public')]").exists());
    }

    @Test
    void previews_rows_with_columns() throws Exception {
        String id = registerSelfPointingDataSource(MockTenants.ACME, "pg-self-preview");
        String resp =
                mvc.perform(
                                post("/api/datasources/" + id + "/preview")
                                        .header(HEADER_TENANT, MockTenants.ACME)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"table\":\"data_source\",\"schema\":\"public\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.columns").isArray())
                        .andExpect(jsonPath("$.data.columns[?(@ == 'tenant_id')]").exists())
                        // 至少有本数据源自身这一行。
                        .andExpect(jsonPath("$.data.rows[0]").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        // 行里不得出现 secret 明文：data_source 行含 secret_cipher（密文），但绝无明文口令。
        JsonNode root = json.readTree(resp);
        org.assertj.core.api.Assertions.assertThat(root.at("/data/columns").toString())
                .contains("secret_cipher");
    }

    @Test
    void preview_respects_row_limit() throws Exception {
        // 先在 acme 下多存几条，确保 data_source 至少有 >1 行。
        for (int i = 0; i < 3; i++) {
            registerSelfPointingDataSource(MockTenants.ACME, "pg-limit-" + i);
        }
        String id = registerSelfPointingDataSource(MockTenants.ACME, "pg-limit-main");
        mvc.perform(
                        post("/api/datasources/" + id + "/preview")
                                .header(HEADER_TENANT, MockTenants.ACME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"table\":\"data_source\",\"schema\":\"public\",\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(1))
                .andExpect(jsonPath("$.data.rows.length()").value(1));
    }

    @Test
    void cross_tenant_datasource_is_not_found() throws Exception {
        String id = registerSelfPointingDataSource(MockTenants.ACME, "pg-isolation");
        // 另一租户访问 acme 的数据源 id → 404（D9：不泄露存在性）。
        mvc.perform(get("/api/datasources/" + id + "/tables").header(HEADER_TENANT, MockTenants.BETA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void unknown_table_is_not_found_whitelist() throws Exception {
        String id = registerSelfPointingDataSource(MockTenants.ACME, "pg-badtable");
        mvc.perform(
                        post("/api/datasources/" + id + "/preview")
                                .header(HEADER_TENANT, MockTenants.ACME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"table\":\"no_such_table_xyz\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));
    }

    @Test
    void missing_tenant_header_is_bad_request() throws Exception {
        mvc.perform(get("/api/datasources/" + java.util.UUID.randomUUID() + "/tables"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }
}
