package io.hashmatrix.data.ingest.batch;

/**
 * SeaTunnel 批量采集接入<b>占位</b>。
 *
 * <p>真实实现将以 SeaTunnel 配置（source → transform → sink）描述批量全量/增量同步，源/目标统一经
 * Connector SPI 的数据源管理接入，按 {@code df_<tenant>} 隔离域落地。占位阶段以文档与结构先行，
 * 不引入 SeaTunnel 运行时依赖，保持骨架可独立构建。
 *
 * <p>批 vs 流分工：CDC/Flink 走实时增量（{@link io.hashmatrix.data.ingest.flink.CdcSourceStub}），
 * SeaTunnel 走批量全量/周期同步——二者共用同一套 Connector SPI 与租户隔离约定。
 */
public final class SeaTunnelBatchStub {

    private SeaTunnelBatchStub() {
        throw new AssertionError("placeholder: no instances until SeaTunnel batch job is wired");
    }
}
