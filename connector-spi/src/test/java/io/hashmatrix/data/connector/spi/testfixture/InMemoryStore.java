package io.hashmatrix.data.connector.spi.testfixture;

import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.TableMeta;
import io.hashmatrix.data.connector.spi.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内测试数据库（仅测试用）：按数据源 URL 隔离若干表，每表持有结构 + 行。
 * 供契约测试在连接前 seed 已知脱敏数据，再经 SPI 校验。
 */
public final class InMemoryStore {

    private static final Map<String, Map<String, Table>> DBS = new ConcurrentHashMap<>();

    private InMemoryStore() {}

    /** 清空某 URL 下全部表。 */
    public static void reset(String url) {
        DBS.put(url, new ConcurrentHashMap<>());
    }

    /** 定义一张表的结构。 */
    public static void defineTable(String url, TableMeta meta) {
        db(url).put(meta.ref().qualifiedName(), new Table(meta));
    }

    /** 追加 seed 行。 */
    public static void seed(String url, TableRef ref, List<Record> rows) {
        table(url, ref).rows.addAll(rows);
    }

    static Map<String, Table> db(String url) {
        return DBS.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
    }

    static Table table(String url, TableRef ref) {
        Table t = db(url).get(ref.qualifiedName());
        if (t == null) {
            throw new IllegalStateException("no such table: " + ref.qualifiedName());
        }
        return t;
    }

    /** 单表：结构 + 行（拷贝语义由调用方保证）。 */
    static final class Table {
        final TableMeta meta;
        final List<Record> rows = new ArrayList<>();

        Table(TableMeta meta) {
            this.meta = meta;
        }
    }
}
