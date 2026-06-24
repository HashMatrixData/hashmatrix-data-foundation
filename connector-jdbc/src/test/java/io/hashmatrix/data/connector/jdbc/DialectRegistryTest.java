package io.hashmatrix.data.connector.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 方言按 URL 选取 + 兜底回退；引用规则差异。 */
class DialectRegistryTest {

    private final DialectRegistry registry = DialectRegistry.load();

    @Test
    void selects_builtin_dialects_by_url() {
        assertThat(registry.forUrl("jdbc:postgresql://host:5432/df_acme").name()).isEqualTo("postgresql");
        assertThat(registry.forUrl("jdbc:mysql://host:3306/df_acme").name()).isEqualTo("mysql");
    }

    @Test
    void falls_back_to_ansi_for_unknown_url() {
        assertThat(registry.forUrl("jdbc:h2:mem:df_acme").name()).isEqualTo("ansi");
    }

    @Test
    void quoting_rules_differ_per_dialect() {
        assertThat(registry.forUrl("jdbc:postgresql://h/db").quoteIdentifier("orders")).isEqualTo("\"orders\"");
        assertThat(registry.forUrl("jdbc:mysql://h/db").quoteIdentifier("orders")).isEqualTo("`orders`");
    }

    @Test
    void selects_dialect_by_type_case_insensitive() {
        assertThat(registry.forType("mysql")).get().extracting(Dialect::name).isEqualTo("mysql");
        assertThat(registry.forType("PostgreSQL")).get().extracting(Dialect::name).isEqualTo("postgresql");
    }

    @Test
    void unknown_or_blank_type_yields_empty_not_fallback() {
        // 未知类型不回退 ANSI 兜底——让上层对「不支持的类型」给出明确错误（D8）。
        assertThat(registry.forType("nosuchdb")).isEmpty();
        assertThat(registry.forType("ansi")).isEmpty();
        assertThat(registry.forType(" ")).isEmpty();
        assertThat(registry.forType(null)).isEmpty();
    }

    @Test
    void dialect_builds_standard_jdbc_url_from_components() {
        // URL 形状是方言的职责（D8）：默认 jdbc:<subprotocol>://host:port/db。
        assertThat(registry.forType("mysql").orElseThrow().jdbcUrl("db.example.com", 3306, "acme_demo"))
                .isEqualTo("jdbc:mysql://db.example.com:3306/acme_demo");
        assertThat(registry.forType("postgresql").orElseThrow().jdbcUrl("pg.example.com", 5432, "df_acme"))
                .isEqualTo("jdbc:postgresql://pg.example.com:5432/df_acme");
    }
}
