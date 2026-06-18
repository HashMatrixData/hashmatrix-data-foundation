package io.hashmatrix.data.ingest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;

/** 配额护栏：记录数 / 并行度双约束。 */
class QuotaGuardTest {

    private final IngestJobRequest request =
            new IngestJobRequest(MockTenants.ACME, "orders", new TenantQuota(100, 4));

    @Test
    void passes_within_quota() {
        assertThatCode(() -> QuotaGuard.check(request, 100, 4)).doesNotThrowAnyException();
    }

    @Test
    void rejects_record_overflow() {
        assertThatThrownBy(() -> QuotaGuard.check(request, 101, 4))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void rejects_parallelism_overflow() {
        assertThatThrownBy(() -> QuotaGuard.check(request, 1, 5))
                .isInstanceOf(QuotaExceededException.class);
    }
}
