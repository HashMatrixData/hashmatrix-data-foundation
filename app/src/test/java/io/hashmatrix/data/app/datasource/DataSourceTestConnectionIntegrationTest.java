package io.hashmatrix.data.app.datasource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hashmatrix.data.app.AbstractPostgresTest;
import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/datasources/test} 端到端：把 Testcontainers 的 PostgreSQL 当作一个 {@code postgresql}
 * 型「被连接数据源」，验证真连成功 / 错口令失败回显 / 不泄口令 / 请求校验。
 *
 * <p>口令为容器临时口令（脱敏占位），全程不落库、不入响应（D7）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataSourceTestConnectionIntegrationTest extends AbstractPostgresTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private String body(Map<String, Object> fields) throws Exception {
        return json.writeValueAsString(fields);
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "postgresql");
        m.put("host", pgHost());
        m.put("port", pgPort());
        m.put("database", pgDatabase());
        m.put("username", pgUsername());
        m.put("password", pgPassword());
        return m;
    }

    @Test
    void real_connect_returns_ok_true() throws Exception {
        mvc.perform(
                        post("/api/datasources/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void wrong_password_returns_ok_false_without_leaking_password() throws Exception {
        String wrongPassword = "wrong-secret-DO-NOT-LEAK";
        Map<String, Object> req = validRequest();
        req.put("password", wrongPassword);

        mvc.perform(
                        post("/api/datasources/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(req)))
                // 探测本身是一次成功的 API 调用 → 200，连通结果在 data.ok。
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                // 红线：口令绝不出现在响应任何位置（D7）。
                .andExpect(content().string(Matchers.not(Matchers.containsString(wrongPassword))));
    }

    @Test
    void unsupported_type_is_bad_request() throws Exception {
        Map<String, Object> req = validRequest();
        req.put("type", "nosuchdb");

        mvc.perform(
                        post("/api/datasources/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DATASOURCE_TYPE"));
    }

    @Test
    void blank_required_field_is_validation_error() throws Exception {
        Map<String, Object> req = validRequest();
        req.put("host", "  ");

        mvc.perform(
                        post("/api/datasources/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void out_of_range_port_is_validation_error() throws Exception {
        Map<String, Object> req = validRequest();
        req.put("port", 70000);

        mvc.perform(
                        post("/api/datasources/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
