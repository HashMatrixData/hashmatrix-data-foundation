package io.hashmatrix.data.ingest;

/** 采集作业超出租户配额时抛出。 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
