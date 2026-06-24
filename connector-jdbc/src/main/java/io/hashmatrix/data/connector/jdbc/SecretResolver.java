package io.hashmatrix.data.connector.jdbc;

/**
 * 口令解析：把 {@link io.hashmatrix.data.connector.spi.DataSourceConfig#secretRef() secretRef}
 * （逻辑引用，非明文）在运行期解析为明文口令。
 *
 * <p>解析策略由调用方注入，从而把「口令从哪来」与「如何连接」解耦（凭据红线）：
 * <ul>
 *   <li>默认 {@code System::getenv} —— secretRef 指向环境变量（SPI 文档既定语义）。</li>
 *   <li>应用层可注入「解密 PG 中 secret_cipher」或「测试连接的临时口令」等解析器，
 *       明文<b>不</b>进 {@code DataSourceConfig}、不落库、不入日志（D7）。</li>
 * </ul>
 */
@FunctionalInterface
public interface SecretResolver {

    /** 解析 secretRef 为明文口令；无对应口令返回 {@code null}。 */
    String resolve(String secretRef);

    /** 默认解析器：secretRef 即环境变量名。 */
    SecretResolver FROM_ENV = System::getenv;
}
