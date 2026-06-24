package io.hashmatrix.data.app.datasource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 数据源配置（本租户登记的「被连接」数据源，如 MySQL）—— data-foundation 持久层的聚合根。
 *
 * <p><b>多租户（D9）</b>：每行带 {@link #tenantId}，写时落、读时强制按 {@code X-Tenant-Id} 过滤；
 * {@code (tenant_id, name)} 唯一，租户内同名不可重复、跨租户互不可见。
 *
 * <p><b>凭据红线（D7）</b>：口令<b>绝不明文落库</b>——仅以 {@link #secretCipher}（AES-GCM 密文，
 * 经 {@link io.hashmatrix.data.app.crypto.CryptoService} 加密）持久化；预留 {@link #secretRef} 间接层，
 * 后续可平滑切 ESO/Vault <b>不改表</b>。{@link #toString()} 刻意不输出任何凭据字段，避免进日志。
 *
 * <p><b>连接器中立（D8）</b>：{@link #type} 为数据源方言类型键（如 {@code "mysql"} / {@code "postgresql"}），
 * 由端点据此选方言、拼 JDBC URL，<b>不写死 MySQL</b>。
 */
@Entity
@Table(name = "data_source")
public class DataSourceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 租户路由键（= ICD 的 X-Tenant-Id），数据隔离边界（D9）。 */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 63)
    private String tenantId;

    /** 数据源展示名（租户内唯一）。 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 方言类型键（mysql / postgresql / ...），驱动方言选择（D8）。 */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    /** 目标库/schema 名（"database" 在 PG 为保留字，列名用 db_name）。 */
    @Column(name = "db_name", nullable = false, length = 255)
    private String databaseName;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /** 口令的 AES-GCM 密文（Base64）；<b>明文绝不落库</b>（D7）。 */
    @Column(name = "secret_cipher", nullable = false, length = 4096)
    private String secretCipher;

    /**
     * 凭据间接引用（Secret/Vault 逻辑名）的<b>预留</b>列：M2 用应用层加密（{@link #secretCipher}），
     * 此列留空；后续切 ESO/Vault 时改填引用、密文转空，<b>不改表结构</b>。
     */
    @Column(name = "secret_ref", length = 512)
    private String secretRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** JPA 用。 */
    protected DataSourceEntity() {}

    private DataSourceEntity(
            UUID id,
            String tenantId,
            String name,
            String type,
            String host,
            int port,
            String databaseName,
            String username,
            String secretCipher) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.secretCipher = secretCipher;
    }

    /**
     * 登记一个新数据源。
     *
     * @param secretCipher 已由 {@link io.hashmatrix.data.app.crypto.CryptoService} 加密的口令密文（非明文）
     */
    public static DataSourceEntity create(
            String tenantId,
            String name,
            String type,
            String host,
            int port,
            String databaseName,
            String username,
            String secretCipher) {
        return new DataSourceEntity(
                UUID.randomUUID(), tenantId, name, type, host, port, databaseName, username, secretCipher);
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    /** 口令密文（Base64 AES-GCM）；解密由 {@link io.hashmatrix.data.app.crypto.CryptoService} 在运行期完成。 */
    public String getSecretCipher() {
        return secretCipher;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** 刻意不输出 {@code secretCipher}/{@code secretRef}/{@code username}，避免凭据进日志（D7 红线）。 */
    @Override
    public String toString() {
        return "DataSourceEntity{id=%s, tenantId=%s, name=%s, type=%s, host=%s, port=%d, db=%s}"
                .formatted(id, tenantId, name, type, host, port, databaseName);
    }
}
