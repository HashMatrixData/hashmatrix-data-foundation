package io.hashmatrix.data.ingest.flink;

import io.hashmatrix.data.ingest.IngestJobRequest;
import io.hashmatrix.data.ingest.QuotaGuard;
import io.hashmatrix.data.ingest.TenantQuota;
import java.util.ArrayList;
import java.util.List;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

/**
 * 流采集作业样例骨架——本地 Flink mini-cluster 即可跑通。
 *
 * <p>骨架链路：有界源 → 配额自检 → 按租户路由（{@link TenantRoutingMapper}）→ 汇出。真实作业把
 * 有界源替换为 {@code connector-jdbc} 的 {@code RecordReader} 或 Flink-CDC 源（见
 * {@link io.hashmatrix.data.ingest.flink.CdcSourceStub}），汇出替换为湖仓 sink（Paimon/Doris per-tenant）。
 *
 * <p>{@link #run} 不绑定具体 sink、直接收集结果，便于在 mini-cluster 下断言；{@link #main} 演示本地运行。
 */
public final class SampleStreamIngestJob {

    private SampleStreamIngestJob() {
        throw new AssertionError("no instances");
    }

    /**
     * 在给定执行环境上运行采集骨架，返回路由后的行（收集自 mini-cluster）。
     *
     * @param env        Flink 执行环境（本地或集群）
     * @param request    采集作业请求（含租户与配额）
     * @param sourceRows 有界源数据（脱敏占位；真实场景来自 Connector SPI / CDC）
     */
    public static List<String> run(StreamExecutionEnvironment env, IngestJobRequest request, List<String> sourceRows)
            throws Exception {
        // 提交前配额自检：拟处理量与并行度不得超租户配额
        QuotaGuard.check(request, sourceRows.size(), env.getParallelism());

        DataStream<String> routed = env.fromCollection(sourceRows)
                .map(new TenantRoutingMapper(request.tenantCatalog(), request.sourceTable()))
                .name("tenant-route");

        List<String> collected = new ArrayList<>();
        try (CloseableIterator<String> it = routed.executeAndCollect()) {
            it.forEachRemaining(collected::add);
        }
        return collected;
    }

    /** 本地演示：单并行度 mini-cluster 跑 tenant-demo 的两行脱敏数据。 */
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        IngestJobRequest request = new IngestJobRequest("tenant-demo", "orders", TenantQuota.defaults());
        run(env, request, List.of("1,demo,100", "2,demo,200")).forEach(System.out::println);
    }
}
