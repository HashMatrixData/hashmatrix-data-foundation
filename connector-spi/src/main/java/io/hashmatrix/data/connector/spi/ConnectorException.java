package io.hashmatrix.data.connector.spi;

/**
 * Connector 操作失败的统一非受检异常。
 *
 * <p>实现方应把底层异常（如 {@link java.sql.SQLException}）包装为本类抛出，使主干与上层
 * （采集作业 / 元数据扫描）以统一方式处理失败，避免泄漏特定数据源的受检异常类型。
 */
public class ConnectorException extends RuntimeException {

    public ConnectorException(String message) {
        super(message);
    }

    public ConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
