package io.hashmatrix.data.connector.spi;

import java.util.Iterator;

/**
 * 行读取（主链路·搬行）。流式迭代某表的行，供采集作业搬运。
 *
 * <p>{@link #read()} 返回的 {@link Iterator} 惰性拉取底层游标；调用方<b>必须</b>
 * {@code try-with-resources} 关闭本 reader 以释放连接/游标。读失败包装为 {@link ConnectorException}。
 */
public interface RecordReader extends AutoCloseable {

    /** 读出行的结构描述（列顺序与 {@link Record} 字段一致）。 */
    TableMeta schema();

    /** 行迭代器（惰性）。 */
    Iterator<Record> read();

    @Override
    void close();
}
