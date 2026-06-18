package io.hashmatrix.data.connector.jdbc.dialect.oceanbase;

import io.hashmatrix.data.connector.jdbc.Dialect;

/**
 * OceanBase 方言插件占位。
 *
 * <p><b>占位说明</b>：当前仅声明 URL 匹配（{@code jdbc:oceanbase:}）。OceanBase 有 MySQL / Oracle
 * 双兼容模式，标识符引用规则随模式不同（反引号 / 双引号）——真实模式判定与引用规则待选型确认后
 * 覆写 {@link #quoteIdentifier(String)} 补全（信创交付期）。占位阶段沿用默认 ANSI 双引号。
 */
public final class OceanBaseDialect implements Dialect {

    @Override
    public String name() {
        return "oceanbase";
    }

    @Override
    public boolean matches(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oceanbase:");
    }
}
