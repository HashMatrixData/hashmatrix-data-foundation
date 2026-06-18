package io.hashmatrix.data.ingest;

/**
 * 租户采集配额：约束单个采集作业可消耗的资源上限。多租户公平性的最小护栏。
 *
 * <p>真实配额由 {@code control-plane} 在租户开通时下发；此处提供数据结构与一个保守默认值，
 * 供作业在提交前经 {@link QuotaGuard} 自检。
 *
 * @param maxRecordsPerRun 单次运行最大处理行数
 * @param maxParallelism   最大并行度
 */
public record TenantQuota(long maxRecordsPerRun, int maxParallelism) {

    public TenantQuota {
        if (maxRecordsPerRun <= 0) {
            throw new IllegalArgumentException("maxRecordsPerRun must be > 0");
        }
        if (maxParallelism <= 0) {
            throw new IllegalArgumentException("maxParallelism must be > 0");
        }
    }

    /** 保守默认配额（占位；生产由 control-plane 下发覆盖）。 */
    public static TenantQuota defaults() {
        return new TenantQuota(1_000_000L, 4);
    }
}
