package io.hashmatrix.data.connector.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 数据源连接配置（不可变）。
 *
 * <p><b>凭据红线</b>：本配置<b>不持有明文口令</b>。{@link #username()} 为登录名，真正的口令经
 * {@link #secretRef()}（指向 K8s Secret / Vault / 环境变量的<b>逻辑引用</b>）在运行期由实现方解析，
 * 严禁把口令写入配置、源码或提交历史。测试一律用脱敏占位（{@code acme} / {@code tenant-demo}）。
 *
 * <p>{@code type} 为连接器类型键（如 {@code "jdbc"}），用于
 * {@link ConnectorRegistry#require(String)} 选取实现；{@code options} 为方言/驱动级附加参数。
 */
public final class DataSourceConfig {

    private final String type;
    private final String url;
    private final String username;
    private final String secretRef;
    private final Map<String, String> options;

    private DataSourceConfig(Builder builder) {
        this.type = require(builder.type, "type");
        this.url = require(builder.url, "url");
        this.username = builder.username;
        this.secretRef = builder.secretRef;
        this.options = Map.copyOf(builder.options);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** 连接器类型键（如 {@code "jdbc"}）。 */
    public String type() {
        return type;
    }

    /** 连接 URL（如 {@code jdbc:postgresql://host:port/db}）；不得内嵌口令。 */
    public String url() {
        return url;
    }

    /** 登录名（可空）。 */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    /** 口令的逻辑引用（Secret/Vault/env 名称）；非明文。运行期由实现方解析。 */
    public Optional<String> secretRef() {
        return Optional.ofNullable(secretRef);
    }

    /** 方言/驱动级附加参数（只读）。 */
    public Map<String, String> options() {
        return options;
    }

    /** 取单个附加参数。 */
    public Optional<String> option(String key) {
        return Optional.ofNullable(options.get(key));
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    /** {@code toString} 刻意不输出 {@code secretRef}，避免凭据引用进日志。 */
    @Override
    public String toString() {
        return "DataSourceConfig{type=%s, url=%s, username=%s, options=%s}"
                .formatted(type, url, username, options.keySet());
    }

    /** {@link DataSourceConfig} 构造器。 */
    public static final class Builder {
        private final String type;
        private String url;
        private String username;
        private String secretRef;
        private final Map<String, String> options = new TreeMap<>();

        private Builder(String type) {
            this.type = type;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder secretRef(String secretRef) {
            this.secretRef = secretRef;
            return this;
        }

        public Builder option(String key, String value) {
            this.options.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public DataSourceConfig build() {
            return new DataSourceConfig(this);
        }
    }
}
