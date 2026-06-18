package io.hashmatrix.data.connector.spi;

/**
 * 行写入（主链路·搬行）。把行写入目标表，供采集作业落地。
 *
 * <p>实现可缓冲批量写；调用方应在结束前 {@link #flush()}，并以 {@code try-with-resources}
 * 关闭以保证残留缓冲落盘。写失败包装为 {@link ConnectorException}。
 */
public interface RecordWriter extends AutoCloseable {

    /** 写入一行（可能进入缓冲，未必立即落库）。 */
    void write(Record record);

    /** 刷出缓冲，确保已写行落库。 */
    void flush();

    @Override
    void close();
}
