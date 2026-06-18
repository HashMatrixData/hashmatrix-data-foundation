package io.hashmatrix.data.connector.jdbc.dialect.kingbase;

import io.hashmatrix.data.connector.jdbc.Dialect;

/**
 * 人大金仓（KingbaseES）方言插件占位。
 *
 * <p><b>占位说明</b>：当前仅声明 URL 匹配（{@code jdbc:kingbase8:}）与默认 ANSI 双引号标识符。
 * KingbaseES 高度兼容 PostgreSQL，真实差异（兼容模式、类型映射）待选型确认后在本类补全（信创交付期）。
 */
public final class KingbaseDialect implements Dialect {

    @Override
    public String name() {
        return "kingbase";
    }

    @Override
    public boolean matches(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:kingbase8:");
    }
}
