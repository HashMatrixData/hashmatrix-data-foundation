package io.hashmatrix.data.ingest;

/** 作业提交前的配额自检护栏。 */
public final class QuotaGuard {

    private QuotaGuard() {
        throw new AssertionError("no instances");
    }

    /**
     * 校验作业拟处理量与并行度未超租户配额。
     *
     * @throws QuotaExceededException 超出配额
     */
    public static void check(IngestJobRequest request, long plannedRecords, int parallelism) {
        TenantQuota quota = request.quota();
        if (plannedRecords > quota.maxRecordsPerRun()) {
            throw new QuotaExceededException(
                    "tenant '%s' planned %d records exceeds quota %d"
                            .formatted(request.tenantId(), plannedRecords, quota.maxRecordsPerRun()));
        }
        if (parallelism > quota.maxParallelism()) {
            throw new QuotaExceededException(
                    "tenant '%s' parallelism %d exceeds quota %d"
                            .formatted(request.tenantId(), parallelism, quota.maxParallelism()));
        }
    }
}
