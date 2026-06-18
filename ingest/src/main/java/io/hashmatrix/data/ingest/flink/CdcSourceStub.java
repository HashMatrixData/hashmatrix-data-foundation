package io.hashmatrix.data.ingest.flink;

/**
 * Flink-CDC 流式源接入<b>占位</b>。
 *
 * <p>真实实现将以 Flink-CDC（如 {@code flink-connector-postgres-cdc}）构建一个增量快照 + binlog/WAL
 * 订阅的 {@code Source<...>}，按租户隔离域订阅 {@code df_<tenant>} 下的表，接入
 * {@link SampleStreamIngestJob} 的链路替换其有界源。CDC 依赖坐标已在根 POM 的
 * {@code flink-cdc.version} 预留，按选型确认后启用。
 *
 * <p>占位阶段不引入 CDC 重依赖，保持骨架可独立构建（参见根 POM 注释）。
 */
public final class CdcSourceStub {

    private CdcSourceStub() {
        throw new AssertionError("placeholder: no instances until Flink-CDC source is wired");
    }
}
