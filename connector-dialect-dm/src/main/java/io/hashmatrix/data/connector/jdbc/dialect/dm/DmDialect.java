package io.hashmatrix.data.connector.jdbc.dialect.dm;

import io.hashmatrix.data.connector.jdbc.Dialect;

/**
 * 达梦（DM8）方言插件占位。
 *
 * <p><b>占位说明</b>：当前仅声明 URL 匹配（{@code jdbc:dm:}）与默认 ANSI 双引号标识符，证明
 * 「加方言插件 jar 即可接入新库、主干零改动」。真实分页语法、类型映射、大小写与标识符规则
 * 待达梦驱动选型确认后在本类补全（信创交付期）；届时按需覆写 {@link #quoteIdentifier(String)} 等。
 */
public final class DmDialect implements Dialect {

    @Override
    public String name() {
        return "dm";
    }

    @Override
    public boolean matches(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:dm:");
    }
}
