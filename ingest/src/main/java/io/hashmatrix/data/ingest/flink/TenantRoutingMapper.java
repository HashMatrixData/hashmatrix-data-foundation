package io.hashmatrix.data.ingest.flink;

import org.apache.flink.api.common.functions.MapFunction;

/**
 * 把源行路由到目标租户隔离域：用 per-tenant catalog 给每行打标，体现「采集按租户隔离 catalog/database」。
 *
 * <p>Flink 算子函数须可序列化；catalog/table 在构造期解析后随函数分发，算子内不读线程级上下文。
 */
final class TenantRoutingMapper implements MapFunction<String, String> {

    private static final long serialVersionUID = 1L;

    private final String tenantCatalog;
    private final String targetTable;

    TenantRoutingMapper(String tenantCatalog, String targetTable) {
        this.tenantCatalog = tenantCatalog;
        this.targetTable = targetTable;
    }

    @Override
    public String map(String sourceRow) {
        // 占位转换：真实作业此处把 Connector SPI 读出的 Record 落到 df_<tenant>.<table>
        return tenantCatalog + "." + targetTable + " <= " + sourceRow;
    }
}
