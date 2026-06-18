package io.hashmatrix.data.connector.spi;

/**
 * 一条已打开的数据源连接。由 {@link Connector#connect(DataSourceConfig)} 创建。
 *
 * <p>同一连接同时提供旁路（{@link #metadataScanner()} 取结构）与主链路
 * （{@link #reader(TableRef)} / {@link #writer(TableRef)} 搬行）两类能力——
 * 体现「连接器只实现一次、两类采集复用」（AD-15）。
 *
 * <p>{@link AutoCloseable}：调用方以 {@code try-with-resources} 管理生命周期。
 */
public interface ConnectorConnection extends AutoCloseable {

    /** 元数据扫描器（旁路·取结构）。 */
    MetadataScanner metadataScanner();

    /** 表行读取器（主链路·搬行）。 */
    RecordReader reader(TableRef table);

    /** 表行写入器（主链路·搬行）。 */
    RecordWriter writer(TableRef table);

    /** 连接是否仍可用。 */
    boolean isValid();

    @Override
    void close();
}
