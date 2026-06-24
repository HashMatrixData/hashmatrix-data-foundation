package io.hashmatrix.data.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 入口级 HTTP 集成测试（末端守护）：真起容器、走 HTTP，守护 #4 引入的端口分离不变量——
 * 「应用口出业务 API（ConnectorController 200）/ 管理口出 actuator readiness（UP）」。这条
 * 跨边界不变量无任何单元测试能独立守护（现有 ConnectorControllerTest 仅直调方法、不走 HTTP）。
 *
 * <p>命名用 {@code *Test} 而非 {@code *IT}：本仓未绑定 failsafe，仅 surefire 在 test 阶段生效，
 * 故 {@code *IT} 会被 {@code mvn verify} 静默跳过（与既有 SampleStreamIngestJobTest 约定一致）。
 *
 * <p>{@code management.server.port=0} 让管理端口取随机口（与 RANDOM_PORT 的应用口必然不同），
 * 从而可断言二者确为不同端口、且各端点只在对应口可达。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class EntryPointHttpTest extends AbstractPostgresTest {

    @LocalServerPort
    private int appPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void business_api_reachable_on_app_port() {
        ResponseEntity<String> resp = rest.getForEntity(url(appPort, "/api/connectors"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody())
                .contains("jdbc")
                .contains("postgresql", "mysql", "dm", "kingbase", "oceanbase", "ansi");
    }

    @Test
    void actuator_readiness_reachable_on_management_port() {
        ResponseEntity<String> resp =
                rest.getForEntity(url(managementPort, "/actuator/health/readiness"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void app_and_management_ports_are_separated() {
        assertThat(managementPort).isNotEqualTo(appPort);
    }

    @Test
    void actuator_not_served_on_app_port() {
        // 端口分离的反向证据：actuator 已迁至管理口，应用口不再暴露 /actuator/**。
        // 只断言「不被成功服务」（非 2xx）——具体落到哪个错误码由 starter-web 的未命中路由处理决定
        // （此处经 GlobalExceptionHandler 落 500），不在本测试关注范围；关键是应用口上 readiness 不可成功访问。
        // 注：该未命中请求会在 starter-web 留一条预期内的 ERROR 日志，是本断言的正常副产物、非测试失败信号。
        ResponseEntity<String> resp =
                rest.getForEntity(url(appPort, "/actuator/health/readiness"), String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("actuator readiness 不应在业务口被成功服务（应仅在管理口可达）")
                .isFalse();
    }

    private static String url(int port, String path) {
        return "http://localhost:" + port + path;
    }
}
