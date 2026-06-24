package io.hashmatrix.data.app.datasource;

import io.hashmatrix.data.app.crypto.CryptoService;
import io.hashmatrix.data.connector.jdbc.Dialect;
import io.hashmatrix.data.connector.jdbc.DialectRegistry;
import io.hashmatrix.data.connector.jdbc.JdbcConnector;
import io.hashmatrix.data.connector.spi.ColumnMeta;
import io.hashmatrix.data.connector.spi.ConnectorConnection;
import io.hashmatrix.data.connector.spi.DataSourceConfig;
import io.hashmatrix.data.connector.spi.Record;
import io.hashmatrix.data.connector.spi.RecordReader;
import io.hashmatrix.data.connector.spi.TableRef;
import io.hashmatrix.starter.web.BusinessException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 列库表 / 预览（WP4）：以<b>已保存</b>的数据源（解密口令）打开真连，旁路读结构 / 受限取样。
 *
 * <p><b>租户隔离（D9）</b>：一律经 {@link DataSourceRepository#findByIdAndTenantId} 取数据源——
 * 非本租户的 id 视同不存在（404），不泄露存在性。
 *
 * <p><b>凭据红线（D7）</b>：口令经 {@link CryptoService#decrypt} 解密后，经 {@link JdbcConnector} 的
 * 临时 {@code SecretResolver}「即用即弃」，不进 {@link DataSourceConfig}、不入日志。
 *
 * <p><b>预览防注入</b>：请求表名一律先按真实元数据 {@link #listTables 白名单}校验，命中后用其<b>规范</b>
 * {@link TableRef}（经方言引用标识符）构造查询——绝不把客户端字符串拼进 SQL；行数经
 * {@link ConnectorConnection#reader(TableRef, int) setMaxRows} 封顶（方言中立，非 {@code LIMIT} 拼接）。
 */
@Service
public class DataSourceCatalogService {

    /** 预览默认/最大行数（上限护栏，防止预览拉全表）。 */
    public static final int DEFAULT_PREVIEW_LIMIT = 100;

    public static final int MAX_PREVIEW_LIMIT = 1000;

    private final DataSourceRepository repository;
    private final CryptoService crypto;
    private final DialectRegistry dialects;

    public DataSourceCatalogService(
            DataSourceRepository repository, CryptoService crypto, DialectRegistry dialects) {
        this.repository = repository;
        this.crypto = crypto;
        this.dialects = dialects;
    }

    /** 列出某数据源（本租户）下的表。 */
    public List<TableRef> listTables(String tenantId, UUID dataSourceId) {
        DataSourceEntity ds = require(tenantId, dataSourceId);
        try (ConnectorConnection conn = open(ds)) {
            return conn.metadataScanner().listTables(ds.getDatabaseName(), null);
        }
    }

    /**
     * 预览某表前 N 行（N 经钳制 + 驱动侧封顶）。
     *
     * @param schema 可空（如 MySQL 无 schema 层级）；给定则参与白名单匹配
     * @throws BusinessException 数据源/表不存在（404）
     */
    public PreviewResult preview(
            String tenantId, UUID dataSourceId, String table, String schema, Integer limit) {
        DataSourceEntity ds = require(tenantId, dataSourceId);
        int effectiveLimit = clampLimit(limit);
        try (ConnectorConnection conn = open(ds)) {
            // 白名单：请求表必须真实存在于元数据中，并取其规范 TableRef（防注入）。
            TableRef ref =
                    conn.metadataScanner().listTables(ds.getDatabaseName(), schema).stream()
                            .filter(t -> t.name().equals(table))
                            .filter(t -> schema == null || schema.isBlank() || schema.equals(t.schema()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    HttpStatus.NOT_FOUND,
                                                    "TABLE_NOT_FOUND",
                                                    "表不存在：" + table));

            try (RecordReader reader = conn.reader(ref, effectiveLimit)) {
                List<String> columns =
                        reader.schema().columns().stream().map(ColumnMeta::name).toList();
                List<Map<String, Object>> rows = new ArrayList<>();
                Iterator<Record> it = reader.read();
                // 二次截断兜底：即便实现未下推上限，也只取 effectiveLimit 行。
                while (it.hasNext() && rows.size() < effectiveLimit) {
                    rows.add(it.next().asMap());
                }
                return new PreviewResult(columns, rows, effectiveLimit);
            }
        }
    }

    private DataSourceEntity require(String tenantId, UUID dataSourceId) {
        return repository
                .findByIdAndTenantId(dataSourceId, tenantId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "数据源不存在"));
    }

    /** 用保存的数据源打开真连（解密口令 → SecretResolver 即用即弃 → connect）。 */
    private ConnectorConnection open(DataSourceEntity ds) {
        Dialect dialect =
                dialects.forType(ds.getType())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "UNSUPPORTED_DATASOURCE_TYPE",
                                                "不支持的数据源类型：" + ds.getType()));
        String url = dialect.jdbcUrl(ds.getHost(), ds.getPort(), ds.getDatabaseName());
        String password = crypto.decrypt(ds.getSecretCipher());
        JdbcConnector connector = new JdbcConnector(ref -> password);
        DataSourceConfig config =
                DataSourceConfig.builder(JdbcConnector.TYPE)
                        .url(url)
                        .username(ds.getUsername())
                        .secretRef("stored")
                        .build();
        return connector.connect(config);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        return Math.min(limit, MAX_PREVIEW_LIMIT);
    }

    /**
     * 预览结果。
     *
     * @param columns 列名（保序）
     * @param rows    行（列名→值，保序）；至多 {@code limit} 行
     * @param limit   生效的行上限（钳制后）
     */
    public record PreviewResult(List<String> columns, List<Map<String, Object>> rows, int limit) {}
}
