package io.hashmatrix.data.app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 持久层集成测试基座：真 PostgreSQL 容器 + Flyway 迁移 + D7 加密主密钥。
 *
 * <p>{@code @SpringBootTest} 上下文加 JPA/datasource/CryptoService 后须有真库与主密钥才能起；本基座统一
 * 提供之，既有上下文/入口测试继承即可零改造回归。容器 SQL 走真 PG（V1 用 PG 专属类型），不退化 H2。
 *
 * <p><b>单例容器模式</b>：容器在静态初始化时启动一次、整个 JVM 共享、<b>不</b>随单个测试类 afterAll 停止
 * （故<b>不</b>用 {@code @Testcontainers}/{@code @Container}）。多个 {@code @SpringBootTest+@AutoConfigureMockMvc}
 * 测试类会缓存复用同一 Spring 上下文（Hikari 锁定首个容器的映射端口）——若容器随某个类结束被停掉、
 * 后续类复用旧上下文就会连到死端口（Connection refused）。单例容器避免这一交互。
 * 容器随 JVM 退出由 Ryuk 回收（CI 默认开启）；本地禁用 Ryuk 时残留容器手动 prune 即可。
 *
 * <p>测试主密钥为<b>脱敏占位</b>（固定 32 字节字面量经 Base64），仅供测试，绝非任何真实密钥。
 */
public abstract class AbstractPostgresTest {

    /** 32 字节脱敏占位主密钥（AES-256）；仅测试用。 */
    private static final String TEST_SECRET_KEY_BASE64 =
            Base64.getEncoder()
                    .encodeToString(
                            "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("df_local")
                    .withUsername("df")
                    .withPassword("df_local_only");

    static {
        // 单例：启动一次，整个测试 JVM 共享，不随任何测试类 afterAll 停止。
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // 用字面回环 IP，避免依赖 JVM 对 "localhost" 的名称解析（某些 CI/容器环境解析受限）。
        registry.add(
                "spring.datasource.url", () -> POSTGRES.getJdbcUrl().replace("localhost", "127.0.0.1"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("hashmatrix.datasource.crypto.secret-key", () -> TEST_SECRET_KEY_BASE64);
    }

    // —— 供「连接被连接数据源」类测试复用本 PG 容器（把它当成一个 postgresql 型被连接源）——

    protected static String pgHost() {
        return POSTGRES.getHost().replace("localhost", "127.0.0.1");
    }

    protected static int pgPort() {
        return POSTGRES.getFirstMappedPort();
    }

    protected static String pgDatabase() {
        return POSTGRES.getDatabaseName();
    }

    protected static String pgUsername() {
        return POSTGRES.getUsername();
    }

    protected static String pgPassword() {
        return POSTGRES.getPassword();
    }
}
