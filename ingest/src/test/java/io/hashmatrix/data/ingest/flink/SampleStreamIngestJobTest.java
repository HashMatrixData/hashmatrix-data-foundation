package io.hashmatrix.data.ingest.flink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.data.ingest.IngestJobRequest;
import io.hashmatrix.data.ingest.QuotaExceededException;
import io.hashmatrix.data.ingest.TenantQuota;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.List;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

/** Flink 流采集骨架的本地 mini-cluster 集成测试：跑通 + 租户路由 + 配额拦截。 */
class SampleStreamIngestJobTest {

    private static StreamExecutionEnvironment localEnv(int parallelism) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        return env;
    }

    @Test
    void runs_on_local_mini_cluster_and_routes_by_tenant() throws Exception {
        IngestJobRequest request = new IngestJobRequest(MockTenants.ACME, "orders", TenantQuota.defaults());
        List<String> out = SampleStreamIngestJob.run(localEnv(1), request, List.of("1,acme,100", "2,acme,250"));

        assertThat(out).hasSize(2);
        assertThat(out).allMatch(line -> line.startsWith("df_acme.orders <= "));
    }

    @Test
    void rejects_job_exceeding_record_quota() {
        // 配额仅 1 行，源给 2 行 → 提交前自检拦截
        TenantQuota tight = new TenantQuota(1, 4);
        IngestJobRequest request = new IngestJobRequest(MockTenants.TENANT_DEMO, "orders", tight);

        assertThatThrownBy(() -> SampleStreamIngestJob.run(localEnv(1), request, List.of("1,demo,1", "2,demo,2")))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("exceeds quota");
    }
}
